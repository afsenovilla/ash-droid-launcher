# Ash Droid Launcher - PC / Steam Deck: redraws the lettering of the game's own images
# at run time (Ruby port of tools/art/relabel.py and launcher/ControlsArt.java), so the
# patch zip ships no game art: only the recipes (ashdroid_art.dat = art.json + pixel fonts).
# Loaded by ashdroid_patches.rb when ashdroid_art.dat is next to it.
#
# Pixels are Integers 0xRRGGBBAA; an image is AshDroidArt::Img (w, h, px Array).

module AshDroidArt
  Img = Struct.new(:w, :h, :px)
  BLACK = 0x000000FF
  CLEAR = 0x00000000

  def self.rgba(r, g, b, a = 255) (r << 24) | (g << 16) | (b << 8) | a end
  def self.alpha(c) c & 0xFF end
  def self.rgb(c) c >> 8 end
  def self.hexcol(s)
    s = s.sub("#", "")
    rgba(s[0, 2].to_i(16), s[2, 2].to_i(16), s[4, 2].to_i(16))
  end

  ACCENT_FROM = "ÁÉÍÓÚÜÑáéíóúüñ"
  ACCENT_TO = "AEIOUUNaeiouun"
  def self.strip_accents(s) s.tr(ACCENT_FROM, ACCENT_TO) end

  # Python's Counter.most_common: by count, ties in first-seen order
  def self.most_common(counts)
    counts.each_with_index.sort_by { |(c, n), i| [-n, i] }.map { |(c, n), _| c }
  end

  # ------------------------------------------------------------------ canvas

  class Canvas
    attr_reader :w, :h
    def initialize(img, grid = 1, offset = [0, 0], dot = 0)
      @img, @g, @ox, @oy, @dot = img, grid, offset[0], offset[1], dot
      @w = (img.w - @ox) / grid
      @h = (img.h - @oy) / grid
    end

    def get(x, y)
      return CLEAR unless x >= 0 && x < @w && y >= 0 && y < @h
      @img.px[(@oy + y * @g) * @img.w + @ox + x * @g]
    end

    def put(x, y, c)
      return unless x >= 0 && x < @w && y >= 0 && y < @h
      @g.times do |yy|
        @g.times do |xx|
          lamp = @dot == 0 || (xx < @dot && yy < @dot)
          xp, yp = @ox + x * @g + xx, @oy + y * @g + yy
          next if xp >= @img.w || yp >= @img.h
          @img.px[yp * @img.w + xp] = lamp ? c : BLACK
        end
      end
    end
  end

  # ------------------------------------------------------------ recipe regions

  def self.pick_char(font, ch)
    [ch, ch.upcase, strip_accents(ch), strip_accents(ch).upcase].each { |c| return c if font[:g].key?(c) }
    strip_accents(ch).upcase
  end

  def self.paste_image(cv, src, rect)
    x, y, w, h = rect
    w.times.to_a.product(h.times.to_a).each do |xx, yy|
      # same order as Python (yy outer, xx inner) is irrelevant: each target pixel set once
      cv.put(x + xx, y + yy, src.px[(yy * src.h / h) * src.w + xx * src.w / w])
    end
  end

  def self.apply_region(cv, r, fonts, buttons, loader)
    if r["image"] && loader
      src = loader.call(r["image"])
      if src
        paste_image(cv, src, r["imageRect"] || r["rect"])
        return
      end
    end
    return if r["onlyImage"]
    x, y, w, h = r["rect"]
    style = r["style"] || "shadow"
    erase = r["erase"] || "row"
    bgx = r["bgx"] || x
    rowbg = {}
    (y...y + h).each do |yy|
      rowbg[yy] = if erase == "transparent" then CLEAR
                  elsif r["bg"] then hexcol(r["bg"])
                  else cv.get(bgx, yy)
                  end
    end
    cnt = {}
    tx0 = ty0 = 10**9
    tx1 = ty1 = -1
    (y...y + h).each do |yy|
      (x...x + w).each do |xx|
        c = cv.get(xx, yy)
        next unless c != rowbg[yy] && alpha(c) > 0
        cnt[c] = (cnt[c] || 0) + 1
        tx0 = xx if xx < tx0
        tx1 = xx if xx > tx1
        ty0 = yy if yy < ty0
        ty1 = yy if yy > ty1
      end
    end
    order = most_common(cnt)
    if style == "outline"
      shade = order[0] || rgba(64, 64, 64)
      fill = order[1] || rgba(248, 248, 248)
    else
      fill = order[0] || rgba(64, 64, 64)
      shade = order[1]
    end
    if r["fill"]
      fill = hexcol(r["fill"])
      if style != "outline"
        rest = order.reject { |c| rgb(c) == rgb(fill) }
        shade = rest[0]
      end
    end
    shade = hexcol(r["shade"]) if r["shade"]
    fills = (r["fills"] || []).map { |c| hexcol(c) }
    if ty1 < 0
      ty0, ty1, tx0 = y, y + h - 1, x
    end
    if erase == "inpaint"
      ink = (r["ink"] || []).map { |c| rgb(hexcol(c)) }
      (y...y + h).each do |yy|
        row = ((x - w)...(x + 2 * w)).map { |xx| cv.get(xx, yy) }
        is_ink = row.map { |c| ink.include?(rgb(c)) }
        (w...2 * w).each do |i|
          next unless is_ink[i]
          (1..w).each do |dd|
            if i - dd >= 0 && !is_ink[i - dd]
              cv.put(x + i - w, yy, row[i - dd]); break
            end
            if i + dd < row.size && !is_ink[i + dd]
              cv.put(x + i - w, yy, row[i + dd]); break
            end
          end
        end
      end
    elsif erase != "none"
      (y...y + h).each { |yy| (x...x + w).each { |xx| cv.put(xx, yy, rowbg[yy]) } }
    end

    if r["bitmap"]
      ox, oy = x + (r["dx"] || 0), y + (r["dy"] || 0)
      r["bitmap"].each_with_index do |row, j|
        row.each_char.with_index do |ch, i|
          if ch == "#" then cv.put(ox + i, oy + j, fill)
          elsif ch == "s" && shade then cv.put(ox + i, oy + j, shade)
          end
        end
      end
      return
    end

    text = (r["text"] || "").dup
    buttons.each { |k, v| text = text.gsub("{#{k}}", v) }
    raw = text
    names = r["font"] || "5x7"
    names = [names] unless names.is_a?(Array)
    bold = r["bold"] ? 1 : 0
    extra = style == "shadow" && shade ? 1 : (style == "outline" ? 2 : 0)
    height = r["height"] || [1, ty1 - ty0 + 1 - extra].max
    font = glyphs = widths = nil
    spacing = r["spacing"] || 1
    tw = 0
    names.each_with_index do |name, fi|
      font = fonts[name]
      blank = ["." * font[:w]] * font[:h]
      text = raw.each_char.map { |ch| pick_char(font, ch) }.join
      glyphs = text.each_char.map { |ch| font[:g][ch] || blank }
      widths = glyphs.map { |g| g[0].size + bold }
      spacing = r["spacing"] || 1
      tw = widths.sum + spacing * (text.size - 1) + extra
      covered = text.each_char.all? { |ch| font[:g].key?(ch) || ch == " " }
      break if fi == names.size - 1 || (covered && tw <= w)
    end
    loop do
      tw = widths.sum + spacing * (text.size - 1) + extra
      break if tw <= w || spacing <= 0
      spacing -= 1
    end
    align = r["align"] || "center"
    ox = if align == "left" then (tx1 >= 0 ? tx0 : x)
         elsif align == "right" then x + w - tw
         else x + (w - tw).div(2)
         end
    ox += r["dx"] || 0
    oy = ty0 + (r["dy"] || 0)
    gx0 = ox + (style == "outline" ? 1 : 0)
    gy0 = oy + (style == "outline" ? 1 : 0)

    corner = r["corner"] ? hexcol(r["corner"]) : nil
    passes = corner && style == "outline" ? [-1, 0, 1] : [0, 1]
    fh = font[:h]
    passes.each do |pass|
      next if pass == 0 && (style == "plain" || !shade)
      cx = gx0
      glyphs.each_with_index do |g, gi|
        top = g.size - fh
        ((-top)...height).each do |ty|
          gy = ty < 0 ? top + ty : top + (ty * fh + fh / 2) / height
          col = fills.empty? ? fill : fills[[0, ty].max * fills.size / height]
          row = g[gy]
          row.size.times do |gx|
            next unless row[gx] == "#"
            (bold + 1).times do |b|
              xp, yp = cx + gx + b, gy0 + ty
              if pass == 1
                cv.put(xp, yp, col)
              elsif pass == -1
                [[-1, -1], [1, -1], [-1, 1], [1, 1]].each { |dx, dy| cv.put(xp + dx, yp + dy, corner) }
              elsif style == "shadow"
                cv.put(xp + 1, yp + 1, shade)
              elsif corner
                [[0, 0], [-1, 0], [1, 0], [0, -1], [0, 1]].each { |dx, dy| cv.put(xp + dx, yp + dy, shade) }
              else
                (-1..1).each { |dy| (-1..1).each { |dx| cv.put(xp + dx, yp + dy, shade) } }
              end
            end
          end
        end
        cx += widths[gi] + spacing
      end
    end
  end

  # Applies every recipe entry of `path` for `lang` ("es": all entries, "any": only "any" ones)
  def self.relabel(img, entries, lang, fonts, buttons, loader)
    img = Img.new(img.w, img.h, img.px.dup)
    entries.each do |e|
      next if lang == "any" && (e["lang"] || "es") != "any"
      cv = Canvas.new(img, e["grid"] || 1, e["offset"] || [0, 0], e["dot"] || 0)
      e["regions"].each { |r| apply_region(cv, r, fonts, buttons, loader) }
    end
    img
  end

  # ------------------------------------------------ controls screen key caps

  module Controls
    module_function

    def classify(base)
      return nil if base.include?("bg") || base.include?("background")
      return :dpad if base.include?("arrow") || base.include?("dir")
      return :special if base.include?("special")
      return :action if base.include?("action")
      return :back if base.include?("back")
      return :use if base.include?("use")
      return :hide if base =~ /(\bf\d+|_f\d+|\Af\d+)/
      nil
    end

    def dist(a, b)
      (((a >> 24) & 255) - ((b >> 24) & 255)).abs + (((a >> 16) & 255) - ((b >> 16) & 255)).abs +
        (((a >> 8) & 255) - ((b >> 8) & 255)).abs
    end

    def mode_color(colors, exclude = nil, min_dist = 0)
      cnt = {}
      colors.each do |c|
        next if (c & 255) < 200
        k = c | 255
        next if exclude && dist(k, exclude) < min_dist
        cnt[k] = (cnt[k] || 0) + 1
      end
      AshDroidArt.most_common(cnt)[0]
    end

    def pixel_scale(img)
      return 1 if img.w.odd? || img.h.odd?
      (0...img.h).step(2) do |y|
        (0...img.w).step(2) do |x|
          c = img.px[y * img.w + x]
          return 1 if img.px[y * img.w + x + 1] != c || img.px[(y + 1) * img.w + x] != c ||
                      img.px[(y + 1) * img.w + x + 1] != c
        end
      end
      2
    end

    def scale(img, f)
      if f < 1 # down by 1/f (nearest)
        d = (1 / f).round
        w, h = img.w / d, img.h / d
        Img.new(w, h, Array.new(w * h) { |i| img.px[(i / w) * d * img.w + (i % w) * d] })
      else
        w, h = img.w * f, img.h * f
        Img.new(w, h, Array.new(w * h) { |i| img.px[((i / w) / f) * img.w + (i % w) / f] })
      end
    end

    def draw_text(img, s, x, y, sc, color, shadow, font)
      gw, gh = 5, 7
      (shadow ? [0, 1] : [1]).each do |pass|
        off = pass == 0 ? sc : 0
        col = pass == 0 ? shadow : color
        cx = x
        s.each_char do |ch|
          g = (font[:g][ch.upcase] || ["....."] * gh).last(gh).map { |r| r.ljust(gw, ".")[0, gw] }
          gh.times do |gy|
            gw.times do |gx|
              next unless g[gy][gx] == "#"
              [0, 1].each { |b| fill(img, cx + (gx + b) * sc + off, y + gy * sc + off, sc, col) }
            end
          end
          cx += (gw + 2) * sc
        end
      end
    end

    def fill(img, x, y, s, c)
      (y...y + s).each { |yy| (x...x + s).each { |xx| img.px[yy * img.w + xx] = c if xx >= 0 && xx < img.w && yy >= 0 && yy < img.h } }
    end

    def text_width(s, sc) s.empty? ? 0 : (s.size * 7 - 1) * sc end

    def relabel(full, label, font)
      sc = pixel_scale(full)
      orig = sc == 1 ? full : scale(full, 1.0 / sc)
      w, h = orig.w, orig.h
      bmp = Img.new(w, h, orig.px.dup)
      px = bmp.px
      minx, miny, maxx, maxy = w, h, -1, -1
      h.times { |y| w.times { |x| next unless (px[y * w + x] & 255) > 200; minx = x if x < minx; maxx = x if x > maxx; miny = y if y < miny; maxy = y if y > maxy } }
      return nil if maxx < 0
      capw, caph = maxx - minx + 1, maxy - miny + 1
      face = mode_color(px)
      return nil unless face
      mx, mytop, mybot = 2, [3, caph / 7].max, [4, caph / 4].max
      ix0, ix1, iy0, iy1 = minx + mx, maxx - mx, miny + mytop, maxy - mybot
      return nil if ix1 <= ix0 || iy1 <= iy0
      tminy, tmaxy, tp = h, -1, []
      (iy0..iy1).each do |y|
        (ix0..ix1).each do |x|
          c = px[y * w + x]
          next unless (c & 255) > 200 && dist(c, face) > 60
          tp << c
          tminy = y if y < tminy
          tmaxy = y if y > tmaxy
          px[y * w + x] = face
        end
      end
      text_color = tp.empty? ? AshDroidArt.rgba(0x60, 0x60, 0x70) : mode_color(tp)
      shadow = tp.empty? ? nil : mode_color(tp, text_color, 40)
      text_h = tmaxy >= tminy ? tmaxy - tminy + 1 : (iy1 - iy0) * 2 / 3
      if label.size <= 2 && capw > caph + 2
        neww = [caph, 8].max
        half = neww / 2
        dx0 = minx + (capw - neww) / 2
        sq = Img.new(w, h, Array.new(w * h, CLEAR))
        neww.times do |i|
          sx = i < half ? minx + i : maxx - (neww - 1 - i)
          h.times { |y| sq.px[y * w + dx0 + i] = bmp.px[y * w + sx] if dx0 + i >= 0 && dx0 + i < w }
        end
        bmp = sq
        m = ix0 - minx
        ix0, ix1 = dx0 + m, dx0 + neww - 1 - m
      end
      txt = label.upcase
      availw = ix1 - ix0 + 1
      ps = [1, [(text_h + 1) / 8, (availw - 4) / [1, text_width(txt, 1)].max].min].max
      ps = 1 if text_width(txt, ps) > availw
      tw, th = text_width(txt, ps), 7 * ps
      cy = tmaxy >= tminy ? (tminy + tmaxy + 1) / 2 : (iy0 + iy1 + 1) / 2
      draw_text(bmp, txt, ix0 + (availw - tw) / 2, cy - th / 2, ps, text_color, shadow, font)
      sc == 1 ? bmp : scale(bmp, sc)
    end

    def rect(img, x0, y0, x1, y1, c)
      ([y0, 0].max...[y1, img.h].min).each { |y| ([x0, 0].max...[x1, img.w].min).each { |x| img.px[y * img.w + x] = c } }
    end

    def lum(c) ((c >> 24) & 255) + ((c >> 16) & 255) + ((c >> 8) & 255) end

    # face (most common), hi (lighter), mid and ink (the next darker ones) of a key cap
    def palette(img)
      cnt = {}
      img.px.each { |c| next unless (c & 255) > 200; k = c | 255; cnt[k] = (cnt[k] || 0) + 1 }
      tot = cnt.values.sum
      face = AshDroidArt.most_common(cnt)[0]
      cols = cnt.select { |_, n| n * 100 >= tot }.keys.sort_by { |c| [lum(c), c] }
      hi = lum(cols[-1]) > lum(face) ? cols[-1] : face
      below = cols.select { |c| lum(c) < lum(face) }
      mid = below[-1] || face
      ink = below.size > 1 ? below[-2] : mid
      [face, hi, mid, ink]
    end

    # Arrow keys -> a D-pad drawn like the game's key caps (light top-left edge, darker
    # bottom-right bevel, concave centre, arrows in the arrow colour), at the art's scale
    def dpad(orig)
      return nil unless orig.px.any? { |c| (c & 255) > 200 }
      sc = pixel_scale(orig)
      small = sc == 1 ? orig : scale(orig, 1.0 / sc)
      face, hi, mid, ink = palette(small)
      w, h = small.w, small.h
      s = [w, h].min - 1
      s -= 1 - s % 2
      a = s / 3
      a += 1 - a % 2
      x0, y0, c = (w - s) / 2, (h - s) / 2, s / 2
      out = lambda do |x, y|
        u, v = x - x0, y - y0
        next true unless u >= 0 && u < s && v >= 0 && v < s
        !((c - a / 2 <= u && u <= c + a / 2) || (c - a / 2 <= v && v <= c + a / 2))
      end
      img = Img.new(w, h, Array.new(w * h, CLEAR))
      h.times do |y|
        w.times do |x|
          next if out.call(x, y)
          next if (out.call(x - 1, y) || out.call(x + 1, y)) && (out.call(x, y - 1) || out.call(x, y + 1))
          col = face
          col = hi if out.call(x, y - 1) || out.call(x - 1, y)
          col = mid if out.call(x, y + 1) || out.call(x, y + 2) || out.call(x + 1, y) || out.call(x + 2, y)
          img.px[y * w + x] = col
        end
      end
      cx, cy = x0 + c, y0 + c
      r = [2, a / 2 - 2].max + 0.5
      (cy - 4..cy + 4).each do |y|
        (cx - 4..cx + 4).each do |x|
          d = (x - cx)**2 + (y - cy)**2
          img.px[y * w + x] = (x - cx) + (y - cy) < 0 ? mid : hi if (r - 1)**2 < d && d <= r * r
        end
      end
      t = [2, (a - 1) / 2 - 1].max
      pos = c - (c - a / 2) / 2 - 1
      4.times do |dn|
        t.times do |i|
          o = pos + t / 2 - i
          (-i..i).each do |j|
            x, y = [[cx + j, cy - o], [cx + j, cy + o], [cx - o, cy + j], [cx + o, cy + j]][dn]
            img.px[y * w + x] = ink
          end
        end
      end
      sc == 1 ? img : scale(img, sc)
    end

    # nil = leave the image alone
    def generate(base, img, labels, font)
      kind = classify(base)
      case kind
      when nil then nil
      when :dpad then dpad(img)
      when :hide then Img.new(img.w, img.h, Array.new(img.w * img.h, CLEAR))
      else
        l = labels[kind.to_s.upcase].to_s.split("/")[0].to_s.strip.split(" ")[0].to_s
        relabel(img, l, font)
      end
    end
  end

  # -------------------------------------------------------------- run time

  # data: {"recipes" => art.json, "fonts" => {name => {w:, h:, g: {ch => rows}}}}
  def self.load_data(file)
    return nil unless File.exist?(file)
    d = File.open(file, "rb") { |f| Marshal.load(f) }
    fonts = {}
    d["fonts"].each { |n, f| fonts[n] = { w: f["w"], h: f["h"], g: f["g"] } }
    by_path = {}
    d["recipes"]["images"].each { |e| (by_path[e["path"].downcase] ||= []) << e }
    { fonts: fonts, by_path: by_path }
  end
end
