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

  def self.apply_region(cv, r, fonts, buttons, loader, ttf = nil)
    return starburst(cv, r) if r["tips"]
    return draw_halo(cv, r, fonts, buttons) if r["lines"]
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
    if ty1 < 0 || r["anchor"] == "rect"
      ty0, ty1, tx0 = y, y + h - 1, x
    end
    if erase == "inpaint"
      ink = (r["ink"] || []).map { |c| rgb(hexcol(c)) }
      keep = (r["bgset"] || []).map { |c| hexcol(c) }
      (y...y + h).each do |yy|
        row = ((x - w)...(x + 2 * w)).map { |xx| cv.get(xx, yy) }
        is_ink = if keep.empty?
                   row.map { |c| ink.include?(rgb(c)) }
                 else
                   # anything not close to one of the background colours is old lettering
                   row.map do |c|
                     keep.all? { |k| [((c >> 24) - (k >> 24)).abs, (((c >> 16) & 255) - ((k >> 16) & 255)).abs, (((c >> 8) & 255) - ((k >> 8) & 255)).abs].max > 6 }
                   end
                 end
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
    elsif erase == "lerp"
      # gradient backgrounds: each row blends from the colour at bgx to the one at bgx2
      bx2 = r["bgx2"]
      (y...y + h).each do |yy|
        ca, cb = cv.get(bgx, yy), cv.get(bx2, yy)
        (x...x + w).each do |xx|
          t = [[(xx - bgx) / (bx2 - bgx).to_f, 0.0].max, 1.0].min
          ch = [24, 16, 8, 0].map { |sft| p0 = (ca >> sft) & 255; q0 = (cb >> sft) & 255; (p0 + (q0 - p0) * t + 0.5).floor }
          cv.put(xx, yy, rgba(*ch))
        end
      end
    elsif erase != "none"
      (y...y + h).each { |yy| (x...x + w).each { |xx| cv.put(xx, yy, rowbg[yy]) } }
    end

    return draw_ttf(cv, r, buttons, ttf, fill, shade) if r["ttf"]

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
                cv.put(xp + (r["sdx"] || 1), yp + (r["sdy"] || 1), shade)
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

  # Applies the recipe entries of `path` for `lang`: those of that language ("es", "fr", "de"…)
  # and the "any" ones (button names), in order; for "any", only the "any" ones.
  # ttf: ->(font, size, text) { [w, h, alpha floats] } rasterising the game's TrueType fonts.
  def self.relabel(img, entries, lang, fonts, buttons, loader, ttf = nil)
    img = Img.new(img.w, img.h, img.px.dup)
    entries.each do |e|
      el = e["lang"] || "es"
      next unless el == "any" || el == lang
      cv = Canvas.new(img, e["grid"] || 1, e["offset"] || [0, 0], e["dot"] || 0)
      e["regions"].each { |r| apply_region(cv, r, fonts, buttons, loader, ttf) }
    end
    img
  end

  # ------------------------------------------------------------- shared maths
  # Same arithmetic as tools/art/relabel.py and ArtEngine.java. Premultiplied RGBA
  # images are flat Float arrays, 4 values per pixel.

  def self.lanczos(x)
    return 1.0 if x == 0
    return 0.0 unless x > -3.0 && x < 3.0
    px = Math::PI * x
    3.0 * Math.sin(px) * Math.sin(px / 3.0) / (px * px)
  end

  def self.lanczos_weights(sn, dn)
    scale = sn / dn.to_f
    fs = [scale, 1.0].max
    support = 3.0 * fs
    (0...dn).map do |i|
      centre = (i + 0.5) * scale
      lo = [0, (centre - support + 0.5).to_i].max
      hi = [sn, (centre + support + 0.5).to_i].min
      ws = (lo...hi).map { |j| lanczos((j - centre + 0.5) / fs) }
      t = ws.sum
      [lo, t != 0 ? ws.map { |v| v / t } : ws]
    end
  end

  def self.lanczos_resample(src, sw, sh, dw, dh)
    tmp = src
    if dw != sw
      wx = lanczos_weights(sw, dw)
      tmp = Array.new(dw * sh * 4, 0.0)
      sh.times do |y|
        wx.each_with_index do |(lo, ws), i|
          d = (y * dw + i) * 4
          a0 = a1 = a2 = a3 = 0.0
          ws.each_with_index do |wgt, k|
            s = (y * sw + lo + k) * 4
            a0 += src[s] * wgt; a1 += src[s + 1] * wgt; a2 += src[s + 2] * wgt; a3 += src[s + 3] * wgt
          end
          tmp[d] = a0; tmp[d + 1] = a1; tmp[d + 2] = a2; tmp[d + 3] = a3
        end
      end
    end
    out = tmp
    if dh != sh
      wy = lanczos_weights(sh, dh)
      out = Array.new(dw * dh * 4, 0.0)
      wy.each_with_index do |(lo, ws), j|
        dw.times do |x|
          d = (j * dw + x) * 4
          a0 = a1 = a2 = a3 = 0.0
          ws.each_with_index do |wgt, k|
            s = ((lo + k) * dw + x) * 4
            a0 += tmp[s] * wgt; a1 += tmp[s + 1] * wgt; a2 += tmp[s + 2] * wgt; a3 += tmp[s + 3] * wgt
          end
          out[d] = a0; out[d + 1] = a1; out[d + 2] = a2; out[d + 3] = a3
        end
      end
    end
    out.map { |v| v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v) }
  end

  def self.dilate(m, w, h, rad)
    return m if rad <= 0
    tmp = Array.new(w * h, 0.0)
    h.times { |y| w.times { |x| v = 0.0; ([0, x - rad].max...[w, x + rad + 1].min).each { |xx| e = m[y * w + xx]; v = e if e > v }; tmp[y * w + x] = v } }
    out = Array.new(w * h, 0.0)
    h.times { |y| w.times { |x| v = 0.0; ([0, y - rad].max...[h, y + rad + 1].min).each { |yy| e = tmp[yy * w + x]; v = e if e > v }; out[y * w + x] = v } }
    out
  end

  def self.dilate_disc(m, w, h, rad)
    return m if rad <= 0
    offs = []
    (-rad..rad).each { |dy| (-rad..rad).each { |dx| offs << [dx, dy] if dx * dx + dy * dy <= rad * rad } }
    out = Array.new(w * h, 0.0)
    h.times do |y|
      w.times do |x|
        v = 0.0
        offs.each do |dx, dy|
          xp, yp = x + dx, y + dy
          next unless xp >= 0 && xp < w && yp >= 0 && yp < h
          e = m[yp * w + xp]
          v = e if e > v
        end
        out[y * w + x] = v
      end
    end
    out
  end

  def self.blur(ch, w, h, sigma)
    return ch if sigma <= 0
    rad = [1, (sigma * 3 + 0.999).to_i].max
    k = (-rad..rad).map { |i| Math.exp(-(i * i) / (2.0 * sigma * sigma)) }
    t = k.sum
    k = k.map { |v| v / t }
    tmp = Array.new(w * h, 0.0)
    h.times { |y| w.times { |x| s = 0.0; (-rad..rad).each { |i| s += ch[y * w + [[x + i, 0].max, w - 1].min] * k[i + rad] }; tmp[y * w + x] = s } }
    out = Array.new(w * h, 0.0)
    h.times { |y| w.times { |x| s = 0.0; (-rad..rad).each { |i| s += tmp[[[y + i, 0].max, h - 1].min * w + x] * k[i + rad] }; out[y * w + x] = s } }
    out
  end

  def self.clamp_round(v) ((v < 0 ? 0 : (v > 255 ? 255 : v)) + 0.5).to_i end

  # draws a premultiplied RGBA pixel (0..1 floats) over the canvas
  def self.blend_over(cv, x, y, pr, pg, pb, pa)
    return if pa <= 0.0
    pa = 1.0 if pa > 1.0
    u = cv.get(x, y)
    ua = (u & 255) / 255.0
    oa = pa + ua * (1 - pa)
    return if oa <= 0
    cv.put(x, y, rgba(clamp_round((pr * 255.0 + (u >> 24) * ua * (1 - pa)) / oa),
                      clamp_round((pg * 255.0 + ((u >> 16) & 255) * ua * (1 - pa)) / oa),
                      clamp_round((pb * 255.0 + ((u >> 8) & 255) * ua * (1 - pa)) / oa), (oa * 255 + 0.5).to_i))
  end

  # ----------------------------------------------------------------- TrueType

  TTF_K = 4
  H_BOX = {}

  def self.ink_box(w, h, m)
    x0, y0, x1, y1 = w, h, -1, -1
    h.times do |y|
      w.times do |x|
        next unless m[y * w + x] > 0.0
        x0 = x if x < x0
        x1 = x if x > x1
        y0 = y if y < y0
        y1 = y if y > y1
      end
    end
    x1 >= 0 ? [x0, y0, x1, y1] : nil
  end

  # Smooth lettering with one of the game's TrueType fonts (see relabel.py draw_ttf)
  def self.draw_ttf(cv, r, buttons, ttf, fill, shade)
    k = TTF_K
    return unless ttf
    text = (r["text"] || "").dup
    buttons.each { |bk, v| text = text.gsub("{#{bk}}", v) }
    return if text.empty?
    x, y, w, h = r["rect"]
    cap = r["cap"] || h - 2
    size = hb = nil
    (cap * k * 2).to_i.downto(6) do |s|
      key = "#{r['ttf']}|#{s}"
      unless H_BOX.key?(key)
        ras = ttf.call(r["ttf"], s, "H")
        return unless ras
        H_BOX[key] = ink_box(*ras)
      end
      b = H_BOX[key]
      if b && b[3] - b[1] + 1 <= cap * k
        size, hb = s, b
        break
      end
    end
    return unless size
    ras = ttf.call(r["ttf"], size, text)
    return unless ras
    tw_, th_, tm = ras
    tb = ink_box(tw_, th_, tm)
    return unless tb
    fat = ((r["fat"] || 0) * k).to_i
    gw = tb[2] - tb[0] + 1 + 2 * fat
    bw, bh = gw + k, h * k + 2 * k
    oy = (h * k - (hb[3] - hb[1] + 1)).div(2) - hb[1] + (r["dy"] || 0) * k
    glyph = Array.new(bw * bh, 0.0)
    th_.times do |yy|
      yp = yy + oy
      next unless yp >= 0 && yp < bh
      (tb[0]..tb[2]).each do |xx|
        v = tm[yy * tw_ + xx]
        glyph[yp * bw + xx - tb[0] + fat] = v if v > 0
      end
    end
    sh = Array.new(bw * bh, 0.0)
    use_shade = shade && (r["style"] || "shadow") != "plain"
    if use_shade
      (bh - k).times { |yy| (bw - k).times { |xx| sh[(yy + k) * bw + xx + k] = glyph[yy * bw + xx] } }
    end
    glyph = dilate_disc(glyph, bw, bh, fat)
    fr, fg, fb = (fill >> 24) / 255.0, ((fill >> 16) & 255) / 255.0, ((fill >> 8) & 255) / 255.0
    sr, sg, sb = use_shade ? [(shade >> 24) / 255.0, ((shade >> 16) & 255) / 255.0, ((shade >> 8) & 255) / 255.0] : [0, 0, 0]
    big = Array.new(bw * bh * 4, 0.0)
    (bw * bh).times do |i|
      fa = glyph[i]
      sa = sh[i] * (1 - fa)
      big[i * 4] = fr * fa + (sa != 0 ? sr * sa : 0)
      big[i * 4 + 1] = fg * fa + (sa != 0 ? sg * sa : 0)
      big[i * 4 + 2] = fb * fa + (sa != 0 ? sb * sa : 0)
      big[i * 4 + 3] = fa + sa
    end
    maxw = (w - 2) * k
    if bw > maxw
      big = lanczos_resample(big, bw, bh, maxw, bh)
      bw = maxw
    end
    dw, dh = [1, bw / k].max, [1, bh / k].max
    small = lanczos_resample(big, bw, bh, dw, dh)
    ox = (r["align"] == "left" ? x + 1 : x + (w - dw).div(2)) + (r["dx"] || 0)
    dh.times do |yy|
      dw.times do |xx|
        i = (yy * dw + xx) * 4
        blend_over(cv, ox + xx, y + yy, small[i], small[i + 1], small[i + 2], small[i + 3])
      end
    end
  end

  # ---------------------------------------------------------- star-burst badge

  # Explosion star (see relabel.py starburst)
  def self.starburst(cv, r)
    k = TTF_K
    x, y, w, h = r["rect"]
    cx, cy = r["centre"] || [w / 2.0, h / 2.0]
    tips = r["tips"].sort_by { |t| Math.atan2(t[1] - cy, t[0] - cx) }
    rin = r["rin"]
    bw, bh = w * k, h * k
    n = bw * bh
    base = hexcol(r["layers"][0][1])
    rr = Array.new(n, (base >> 24) / 255.0)
    gg = Array.new(n, ((base >> 16) & 255) / 255.0)
    bb = Array.new(n, ((base >> 8) & 255) / 255.0)
    aa = Array.new(n, 0.0)
    r["layers"].each_with_index do |(sc, colh), li|
      col = hexcol(colh)
      cr, cg, cb = (col >> 24) / 255.0, ((col >> 16) & 255) / 255.0, ((col >> 8) & 255) / 255.0
      pts = []
      tips.each_with_index do |t, i|
        nx = tips[(i + 1) % tips.size]
        a1, a2 = Math.atan2(t[1] - cy, t[0] - cx), Math.atan2(nx[1] - cy, nx[0] - cx)
        a2 += 2 * Math::PI if a2 < a1
        am = (a1 + a2) / 2
        pts << [(cx + (t[0] - cx) * sc) * k, (cy + (t[1] - cy) * sc) * k]
        pts << [(cx + rin * sc * Math.cos(am)) * k, (cy + rin * sc * Math.sin(am)) * k]
      end
      np = pts.size
      bh.times do |yy|
        py = yy + 0.5
        xs = []
        np.times do |i|
          x1, y1 = pts[i]
          x2, y2 = pts[(i + 1) % np]
          xs << x1 + (py - y1) * (x2 - x1) / (y2 - y1) if (y1 <= py) != (y2 <= py)
        end
        xs.sort!
        j = 0
        while j + 1 < xs.size
          a0 = [0, (xs[j] - 0.5).ceil].max
          b0 = [bw - 1, (xs[j + 1] - 0.5).ceil - 1].min
          (a0..b0).each do |xx|
            q = yy * bw + xx
            rr[q] = cr; gg[q] = cg; bb[q] = cb
            aa[q] = 1.0 if li == 0
          end
          j += 2
        end
      end
    end
    sig = (r["blur"] || 0) * k
    rr = blur(rr, bw, bh, sig)
    gg = blur(gg, bw, bh, sig)
    bb = blur(bb, bw, bh, sig)
    big = Array.new(n * 4)
    n.times { |i| a = aa[i]; big[i * 4] = rr[i] * a; big[i * 4 + 1] = gg[i] * a; big[i * 4 + 2] = bb[i] * a; big[i * 4 + 3] = a }
    small = lanczos_resample(big, bw, bh, w, h)
    h.times do |yy|
      w.times do |xx|
        i = (yy * w + xx) * 4
        al = small[i + 3]
        if al <= 0
          cv.put(x + xx, y + yy, CLEAR)
        else
          cv.put(x + xx, y + yy, rgba(clamp_round(small[i] / al * 255), clamp_round(small[i + 1] / al * 255),
                                      clamp_round(small[i + 2] / al * 255), (al * 255 + 0.5).to_i))
        end
      end
    end
  end

  # Pixel-font lettering with outline and soft halo (see relabel.py draw_halo)
  def self.draw_halo(cv, r, fonts, buttons)
    x, y, w, h = r["rect"]
    font = fonts[r["font"] || "card"]
    return unless font
    fill = hexcol(r["fill"] || "#ffffff")
    rcol = hexcol(r["rcol"] || r["fill"] || "#ffffff")
    spacing = r["spacing"] || 1
    col = {}
    mask = Array.new(w * h, 0.0)
    r["lines"].zip(r["tops"]).each do |line, top|
      next unless top
      line = line.dup
      buttons.each { |bk, v| line = line.gsub("{#{bk}}", v) }
      gs = line.each_char.map { |ch| font[:g][pick_char(font, ch)] }
      return if gs.any?(&:nil?)
      widths = gs.map { |g| g.map(&:size).max }
      gx = (w - (widths.sum + spacing * (widths.size - 1))).div(2)
      gs.each_with_index do |g, gi|
        y0 = top - (g.size - font[:h])
        g.each_with_index do |row, dy|
          row.each_char.with_index do |c, dx|
            next unless c == "#"
            xp, yp = gx + dx, y0 + dy
            next unless xp >= 0 && xp < w && yp >= 0 && yp < h
            right = dx + 1 < row.size && row[dx + 1] == "#"
            col[yp * w + xp] = right ? fill : rcol
            mask[yp * w + xp] = 1.0
          end
        end
        gx += widths[gi] + spacing
      end
    end
    halo = blur(dilate(mask, w, h, 2), w, h, r["halo_blur"] || 0.8)
    edge = dilate(mask, w, h, 1)
    hc = hexcol(r["halo"] || "#191919")
    hr, hg, hb = (hc >> 24) / 255.0, ((hc >> 16) & 255) / 255.0, ((hc >> 8) & 255) / 255.0
    h.times do |yy|
      w.times do |xx|
        q = yy * w + xx
        al = [halo[q], 1.0].min
        blend_over(cv, x + xx, y + yy, hr * al, hg * al, hb * al, al)
        cv.put(x + xx, y + yy, BLACK) if edge[q] > 0
        cv.put(x + xx, y + yy, col[q]) if col[q]
      end
    end
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

    # Arrow keys -> a D-pad in the style of the game's key caps (approved design; same
    # algorithm as tools/art/controls_art.py and ControlsArt.java): light cross with an
    # outline in the arrow colour, 3px highlight on the upper-left, darker lower-right edge,
    # 2px of thickness underneath, engraved arrows and a concave ring in the centre.
    def dpad(orig)
      return nil unless orig.px.any? { |c| (c & 255) > 200 }
      sc = pixel_scale(orig)
      small = sc == 1 ? orig : scale(orig, 1.0 / sc)
      face, hi, mid, ink = palette(small)
      side = AshDroidArt.rgba(((ink >> 24) & 255) * 6 / 7, ((ink >> 16) & 255) * 6 / 7, ((ink >> 8) & 255) * 6 / 7)
      w, h = small.w, small.h
      s = [w, h - 3].min
      s -= 1 - s % 2
      a = s / 3
      a += 1 - a % 2
      c = s / 2
      cx, cy = w / 2, (h - 3) / 2
      m = {}
      (cy - c..cy + c).each do |y|
        (cx - c..cx + c).each { |x| m[[x, y]] = true if (x - cx).abs <= a / 2 || (y - cy).abs <= a / 2 }
      end
      2.times do
        m = m.reject do |(x, y), _|
          (!m[[x - 1, y]] || !m[[x + 1, y]]) && (!m[[x, y - 1]] || !m[[x, y + 1]])
        end
      end
      img = Img.new(w, h, Array.new(w * h, CLEAR))
      put = lambda { |x, y, col| img.px[y * w + x] = col if x >= 0 && x < w && y >= 0 && y < h }
      m.each_key { |x, y| put.call(x, y + 2, side) }
      m.each_key do |x, y|
        o = lambda { |dx, dy| !m[[x + dx, y + dy]] }
        col = face
        col = hi if (1..3).any? { |k| o.call(0, -k) || o.call(-k, 0) }
        col = mid if o.call(0, 1) || o.call(1, 0)
        col = ink if o.call(1, 0) || o.call(-1, 0) || o.call(0, 1) || o.call(0, -1)
        put.call(x, y, col)
      end
      t, dist = 4, 10
      4.times do |dn|
        pts = {}
        t.times do |i|
          (-i..i).each do |j|
            d = t / 2 - i
            pts[[[cx + j, cy - dist - d], [cx + j, cy + dist + d], [cx - dist - d, cy + j], [cx + dist + d, cy + j]][dn]] = true
          end
        end
        pts.each_key { |x, y| put.call(x, y, !pts[[x, y - 1]] || !pts[[x - 1, y]] ? ink : mid) }
      end
      (cy - 4..cy + 4).each do |y|
        (cx - 4..cx + 4).each do |x|
          d = (x - cx)**2 + (y - cy)**2
          put.call(x, y, x + y < cx + cy ? mid : hi) if 2.2**2 < d && d <= 3.2**2
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
