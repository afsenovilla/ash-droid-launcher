# Ash Droid Launcher - runtime text patches (generated, loaded via mkxp.json "preloadScript").
# Nothing in the game files is modified: this only rewrites some on-screen texts
# so they talk about the device's buttons instead of keyboard keys.
#
# The launcher replaces the line below with the player's current button mapping:
# keyboard key the game mentions => button name on this device.
ASHDROID_KEYS = {} # __ASHDROID_KEYS__
# System language of the device ("es", "en"…), used when the game's own language can't be read.
ASHDROID_LANG = "en" # __ASHDROID_LANG__
# PC patch on a Steam Deck (the Windows game through Proton, or the native Linux
# engine): detected here, it gets the Deck's buttons like a handheld does on Android.
# Returns "lcd" (Jupiter), "oled" (Galileo), "deck" (Steam says so, model unknown) or nil.
# ASHDROID_DEVICE=pc|deck|lcd|oled in the launch options overrides the detection.
module AshDroidDeck
  DMI = ["/sys/devices/virtual/dmi/id", "Z:/sys/devices/virtual/dmi/id"] # Linux, Wine/Proton

  def self.read(name)
    DMI.each do |dir|
      begin
        f = "#{dir}/#{name}"
        return File.read(f).to_s.strip if File.exist?(f)
      rescue StandardError
      end
    end
    nil
  end

  def self.detect
    forced = ENV["ASHDROID_DEVICE"].to_s.downcase
    return nil if forced == "pc"
    return forced if %w[lcd oled].include?(forced)
    return "deck" if forced == "deck"
    product = read("product_name").to_s
    return "oled" if product =~ /galileo/i
    return "lcd" if product =~ /jupiter/i
    # Steam sets SteamDeck=1 for games started from its Deck interface (also on other
    # SteamOS handhelds, which use the same Xbox-style button names through Steam Input)
    return "deck" if ENV["SteamDeck"] == "1"
    nil
  rescue StandardError
    nil
  end
end

# Keyboard key the game mentions -> Steam Deck button, following mkxp-z's default
# controller bindings (A=C, B=B, X=A, Y=X, L3=Y, R3=Z, L1=L, R1=R). START (the ☰
# button) also opens the menu, like B. LCD and OLED have the same buttons.
ASHDROID_DECK = ASHDROID_KEYS.empty? ? AshDroidDeck.detect : nil
if ASHDROID_DECK
  ASHDROID_KEYS.merge!("C" => "A", "X" => "B / START", "Z" => "X", "D" => "R3",
                       "A" => "Y", "S" => "L3", "Q" => "L1", "W" => "R1")
end

# Native Linux engine of the Steam Deck package (ashdroid_linux/): put its Ruby standard
# library on the load path (the Windows engine's stdlib, if the game lists it, can't load there).
begin
  lib = File.join(Dir.pwd, "ashdroid_linux", "stdlib")
  if File.directory?(lib)
    arch = Dir.glob(File.join(lib, "*-linux*")).find { |d| File.directory?(d) }
    [arch, lib].compact.each { |d| $LOAD_PATH.unshift(d) unless $LOAD_PATH.include?(d) }
  end
rescue StandardError
end

# PC patch: the same file dropped into the Windows/PC game. The launcher always
# fills ASHDROID_KEYS, so an empty table means we are not on the device: keep the
# keyboard texts, the window size option and the PC-only notices.
ASHDROID_PC = ASHDROID_KEYS.empty?

# Small diagnostics log (shown by the launcher under "Diagnostics")
module AshDroidLog
  FILE = "ashdroid_patch.log"
  @n = 0
  def self.log(msg)
    return if @n > 200
    @n += 1
    File.open(FILE, @n == 1 ? "w" : "a") { |f| f.puts(msg.to_s) }
  rescue StandardError
  end
end
AshDroidLog.log("patch loaded; cwd=#{Dir.pwd rescue '?'} ruby=#{RUBY_VERSION} deck=#{ASHDROID_DECK.inspect}")

module AshDroidText
  BUTTON_WORD = { "Default" => "Button", "default" => "button",
                  "Predeterminada" => "Botón", "Predeterminado" => "Botón",
                  "predeterminada" => "botón", "predeterminado" => "botón",
                  "Por defecto" => "Botón", "por defecto" => "botón" }

  # Controls-screen texts the game's Spanish translation leaves in English.
  # Only applied when the game is set to Spanish. The "(Default: X)" part is kept
  # and then turned into the device's button by FIXES below.
  ES = [
    [/\AYou haven't found any Coins! Are you sure you want to quit\?\z/,
      "No has encontrado ninguna ficha. ¿Seguro que quieres salir?"],
    [/\AUsed to open the Pause Menu\..*?(\(Default:[^)]*\))?\s*\z/m,
     "Se usa para abrir el Menú de Pausa. También sirve para salir, cancelar una elección y cancelar un modo."],
    [/\AUsed to exit, cancel a choice or mode, and open the pause menu\..*?(\(Default:[^)]*\))?\s*\z/m,
     "Se usa para salir, cancelar una elección o un modo, y abrir el Menú de Pausa."],
    [/\AWhile moving around, hold to move at a different speed\..*?(\(Default:[^)]*\))?\s*\z/m,
     "Mantenla pulsada al moverte para ir a otra velocidad. Además, tiene otras funciones según el contexto."],
    [/\AHold down while walking to run\..*?(\(Default:[^)]*\))?\s*\z/m,
     "Mantenla pulsada al caminar para correr. También sirve para distintas cosas en distintos menús."],
    [/\APress to open the Ready Menu.*?(\(Default:[^)]*\))?\s*\z/m,
     "Púlsala para abrir el Menú Rápido, donde se pueden usar los objetos registrados y los movimientos de campo disponibles."],
    [/\AUsed to confirm a choice.*?(\(Default:[^)]*\))?\s*\z/m,
     "Se usa para confirmar una elección, interactuar con personas y objetos, y avanzar en el texto."],
    [/\AUse the Arrow keys to move the main character\..*\z/m,
     "Usa las teclas de dirección para mover al personaje principal.\n\nTambién puedes usarlas para seleccionar opciones y navegar por los menús."],
  ]

  # Translation pack for this game (normalized English -> Spanish), copied by the
  # launcher next to this file. Fills the gaps of the game's own translation and
  # also matches strings whose spacing differs from the game's language file.
  PACK_FILE = "ashdroid_es.dat"

  def self.pack
    return @pack if defined?(@pack)
    @pack = begin
      File.exist?(PACK_FILE) ? File.open(PACK_FILE, "rb") { |f| Marshal.load(f) } : {}
    rescue StandardError => e
      AshDroidLog.log("pack load error: #{e.class}: #{e.message}")
      {}
    end
    AshDroidLog.log("pack: exists=#{File.exist?(PACK_FILE)} entries=#{@pack.size}")
    @pack
  end

  # Messages the game's scripts write in English without _INTL, so no language file has them.
  # Their translations are in the retranslation packs; they're swapped in when the message
  # window gets the text.
  HARDCODED = [
    "The harsh sunlight faded!",
    "The heavy rain has lifted!",
    "The mysterious air current has dissipated!",
    "The thunderstorm cleared up.",
    "The tides subsided.",
    "The earthquakes ended.",
    "The heat wave ended.",
    "Select an effect to use.",
    "Can't place that there.",
    "Please remove the mail.",
    "All boxes are full.",
    "You're holding a Pokémon!",
    "You haven't found any Coins! Are you sure you want to quit?"
  ].freeze

  def self.hardcoded(v)
    return v unless v.is_a?(String) && HARDCODED.include?(v)
    t = translate(v)
    return t unless t == v
    retrans_pack[norm(v)] || v
  rescue StandardError
    v
  end

  def self.norm(s)
    s.gsub(/\s+/, " ").strip
  end

  # Messages that only make sense on PC; never shown on the device
  SKIP_MESSAGES = /Helpful Notes|Helpful\b/i

  # The game's "Spanish" becomes "Spanish (Spain)": it still loads the game's
  # Spanish file, but every text in the full retranslation pack replaces it.
  SPAIN_NAME = "Spanish (Spain)"
  SPAIN_PACK_FILE = "ashdroid_es_ES.dat"

  # Full retranslations, one pack per language file of the game
  RETRANS = [[/spanish/i, SPAIN_PACK_FILE], [/french/i, "ashdroid_fr.dat"], [/german/i, "ashdroid_de.dat"]]

  def self.lang_file
    langs = Settings::LANGUAGES
    idx = $PokemonSystem ? $PokemonSystem.language : nil
    langs && idx && langs[idx] ? langs[idx][1].to_s : nil
  rescue StandardError, NameError
    nil
  end

  # Pack of the current language ({} if it has none)
  def self.retrans_pack
    f = lang_file
    hit = f && RETRANS.find { |re, _| f =~ re }
    return {} unless hit
    @packs ||= {}
    @packs[hit[1]] ||= begin
      pk = File.exist?(hit[1]) ? File.open(hit[1], "rb") { |io| Marshal.load(io) } : {}
      AshDroidLog.log("#{hit[1]}: entries=#{pk.size}")
      pk
    rescue StandardError => e
      AshDroidLog.log("#{hit[1]} load error: #{e.class}: #{e.message}")
      {}
    end
  end

  def self.spain_pack
    retrans_pack
  end

  # Fixes for the game's own English (descriptions too long for their boxes),
  # applied only while the game is in English: "@<type>:<id>" like the Spain pack.
  EN_PACK_FILE = "ashdroid_en.dat"

  def self.en_pack
    return @en_pack if defined?(@en_pack)
    @en_pack = begin
      File.exist?(EN_PACK_FILE) ? File.open(EN_PACK_FILE, "rb") { |f| Marshal.load(f) } : {}
    rescue StandardError
      {}
    end
  end

  def self.english?
    langs = Settings::LANGUAGES
    idx = $PokemonSystem ? $PokemonSystem.language : nil
    !!(langs && idx && langs[idx] && langs[idx][1].to_s =~ /english/i)
  rescue StandardError, NameError
    false
  end

  def self.spain?
    langs = Settings::LANGUAGES
    idx = $PokemonSystem ? $PokemonSystem.language : nil
    !!(langs && idx && langs[idx] && langs[idx][0] == SPAIN_NAME)
  rescue StandardError, NameError
    false
  end

  # Retranslation of the current language (or its "#plural" form when {1} is two
  # people), else nil
  def self.spain_lookup(key, first_arg = nil)
    return nil unless key.is_a?(String)
    pk = retrans_pack
    return nil if pk.empty?
    k = norm(key)
    if first_arg.is_a?(String) && first_arg =~ / (y|et|und) |&/
      pl = pk[k + "#plural"]
      return pl if pl
    end
    pk[k]
  rescue StandardError
    nil
  end

  # Spanish for an untranslated source string (before {1}-style formatting), or nil
  def self.lookup(key)
    return nil unless key.is_a?(String) && !pack.empty?
    sp = spanish?
    if @logged_lang != sp
      @logged_lang = sp
      AshDroidLog.log("spanish?=#{sp} lang=#{($PokemonSystem.language rescue 'n/a')} langs=#{(Settings::LANGUAGES.map(&:first) rescue 'n/a')}")
    end
    return nil unless sp
    r = pack[norm(key)]
    @misses = (@misses || 0)
    if r.nil? && key =~ /[a-z]{3}/ && @misses < 25
      @misses += 1
      AshDroidLog.log("miss: #{key[0, 100].inspect}")
    elsif r && (@hits = (@hits || 0) + 1) <= 5
      AshDroidLog.log("hit: #{key[0, 60].inspect}")
    end
    r
  rescue StandardError => e
    AshDroidLog.log("lookup error: #{e.class}: #{e.message}")
    nil
  end

  def self.spanish?
    begin
      langs = Settings::LANGUAGES
      idx = $PokemonSystem ? $PokemonSystem.language : nil
      if langs && idx && langs[idx]
        return Array(langs[idx]).join(" ") =~ /espa|spanish|castellano/i ? true : false
      end
    rescue StandardError, NameError
    end
    ASHDROID_LANG.to_s.start_with?("es")
  end

  def self.translate(s)
    return s unless spanish?
    ES.each do |re, es|
      m = re.match(s)
      next unless m
      key = s[/\(Default:\s*([A-Z0-9]{1,3})\)/, 1]
      return key ? "#{es} (Predeterminada: #{key})" : es
    end
    s
  end

  FIXES = [
    # Controls screen: "(Default: C)" / "(Predeterminada: C)" -> "(Button: A)" / "(Botón: A)"
    [/\((Default|Predeterminad[ao]|Por defecto):\s*([A-Z0-9]{1,3})\)/i, lambda { |m|
      key = m[2].upcase
      btn = ASHDROID_KEYS[key]
      next m[0] unless btn
      word = BUTTON_WORD[m[1]] || (m[1] =~ /^p/i ? "Botón" : "Button")
      "(#{word}: #{btn})"
    }],
    # Fire Ash tips about PC keys: speed-up is Input::AUX2 (= R, keyboard W) and
    # quick save Input::AUX1 (= L, keyboard Q); name the device buttons instead.
    [/(Press|Pulsa) (the|la tecla) 'F' (or|o) 'F7'( key)?/, lambda { |m|
      btn = ASHDROID_KEYS["W"]
      next m[0] unless btn
      m[1] == "Pulsa" ? "Pulsa #{btn}" : "Press #{btn}"
    }],
    [/(Press the left 'Alt' key|Pulsa la tecla 'Alt' izquierda)/, lambda { |m|
      btn = ASHDROID_KEYS["Q"]
      next m[0] unless btn
      m[0].start_with?("Pulsa") ? "Pulsa #{btn}" : "Press #{btn}"
    }],
    # Arrow keys -> D-pad
    [/\bthe Arrow keys\b/i, "the D-pad"],
    [/\bArrow keys\b/i, "D-pad"],
    [/\blas teclas de dirección\b/i, "la cruceta"],
    [/\bteclas de dirección\b/i, "cruceta"],
    [/\bpuedes usarlas\b/i, "puedes usarla"],
    # F1 (mkxp key bindings window) doesn't exist on the device
    [/\A.*(Key Bindings window|ventana de asignación de teclas).*\z/m, lambda { |m|
      es = m[0] =~ /asignaci/
      if ASHDROID_DECK
        es ? "En la Steam Deck los botones se cambian en la configuración del mando de Steam (botón STEAM > Configuración del mando)." :
             "On the Steam Deck, change the buttons in Steam's controller settings (STEAM button > Controller settings)."
      else
        es ? "En esta consola los botones se configuran desde Ash Droid Launcher (\"Botones\")." :
             "On this device, buttons are configured in Ash Droid Launcher (\"Buttons\")."
      end
    }],
    # F8 screenshot: use the device's own screenshot instead
    [/\A.*(captura de pantalla\. Se guarda|Takes? a screenshot).*\z/m, lambda { |m|
      es = m[0] =~ /captura/
      if ASHDROID_DECK
        es ? "Para hacer capturas pulsa STEAM + R1." : "To take screenshots, press STEAM + R1."
      else
        es ? "Para hacer capturas usa la función de captura de pantalla de la consola." :
             "To take screenshots, use your device's screenshot function."
      end
    }],
  ]

  # Spanish contractions broken by placeholders: "de el Pidgey salvaje" -> "del ..."
  # French contractions broken by placeholders: "de le Rattata" -> "du Rattata"
  FRENCH_FIXES = [[/(?<![[:alpha:]])([Dd])e le /, '\1u '], [/(?<![[:alpha:]])([Dd])e les /, '\1es '],
                  [/(?<![[:alpha:]])à le /, 'au '], [/(?<![[:alpha:]])à les /, 'aux '],
                  [/(?<![[:alpha:]])([Dd])e ([aeiouéèêAEIOUÉ])/, "\\1'\\2"]]
  SPAIN_FIXES = [[/(?<![[:alpha:]])([Dd])e el /, '\1el '], [/(?<![[:alpha:]])([Aa]) el /, '\1l ']]

  # The game's Spanish writes "un/a <item>"; pick the article from the item name.
  FEM_WORDS = %w[Ball Bici Caña MT MO DT Baya Piedra Pluma Tabla Llave Carta Medalla Gema Nota Tarjeta Bola
                 Cinta Banda Hierba Semilla Seda Escama Garra Concha Perla Estrella Raíz Miel Leche Lupa Lente Sal Flor Def.]
  MASC_WORDS = %w[Mapa Día Sistema Programa Planeta Tema Clima Agua Hacha Ala Alma Arma Aura Ancla Aula]
  def self.article_for(name)
    words = name.to_s.strip.split(/\s+/)
    first = words.first.to_s.gsub(/[^[:alnum:]áéíóúüñÁÉÍÓÚÜÑ]/, "")
    return "una" if words.include?("Ball") || first =~ /\A(MT|MO|DT)\d*\z/
    return "un" if MASC_WORDS.include?(first) || first =~ /\A(Para|Busca|Quita|Saca|Corta|Lanza|Porta)[a-záéíóú]/
    return "una" if FEM_WORDS.include?(first)
    return "unos" if first =~ /os\z/
    return "unas" if first =~ /as\z/ && first.size > 3
    return "una" if first =~ /(a|ión|dad|tud|umbre|sis)\z/
    "un"
  rescue StandardError
    "un"
  end

  def self.fix_articles(s)
    s.gsub(%r{\b(un/a|un\(a\)) ((?:\\c\[\d+\])?)([^\\.!,?¡¿]+)}) do
      "#{article_for($3)} #{$2}#{$3}"
    end
  end

  def self.fix(s)
    return s unless s.is_a?(String)
    out = translate(s)
    out = fix_articles(out) if out.include?("un/a") || out.include?("un(a)")
    # Text straight on a black screen (e.g. after a defeat) uses white with a pale
    # shadow, which is hard to read: give it a dark shadow instead.
    out = out.gsub("\\c[8]", "<c3=F8F8F8,404048>") if out.include?("\\w[]") && out.include?("\\c[8]")
    SPAIN_FIXES.each { |re, rep| out = out.gsub(re, rep) } if spain?
    FRENCH_FIXES.each { |re, rep| out = out.gsub(re, rep) } if lang_file.to_s =~ /french/i
    (ASHDROID_PC ? [] : FIXES).each do |re, rep|
      next unless out =~ re
      out = rep.is_a?(Proc) ? out.gsub(re) { rep.call(Regexp.last_match) } : out.gsub(re, rep)
    end
    out
  rescue StandardError
    s
  end
end

# Spanish versions of images with English words drawn in them (generated by the
# launcher into ashdroid_es/ from the game's own art). Used only while the game
# language is Spanish; the original files are untouched.
module AshDroidGfx
  # Images redrawn by the launcher. "es" ones replace the originals only while
  # the game is in Spanish; "any" ones (button names only) are used otherwise.
  ES = {}
  ANY = {}
  LOGGED = {}
  # Android: the launcher writes the images and their index to ashdroid_art/.
  INDEX = "ashdroid_art/index.txt"

  begin
    if File.exist?(INDEX)
      File.read(INDEX).each_line do |line|
        lang, key, file = line.chomp.split("\t")
        next unless file
        file = "ashdroid_art/#{file}" unless file.include?("/") # bare file names are relative to the index
        (lang == "any" ? ANY : ES)[key] = file
      end
    end
    AshDroidLog.log("art: #{INDEX} es=#{ES.size} any=#{ANY.size}") if File.exist?(INDEX)
  rescue StandardError
  end

  def self.key_of(path)
    path.tr("\\", "/").sub(/\A\.\//, "").sub(/\.png\z/i, "").downcase
  end

  def self.redirect(path)
    return nil unless path.is_a?(String)
    key = key_of(path)
    alt = AshDroidText.spanish? ? (ES[key] || ANY[key]) : ANY[key]
    res = alt && File.exist?(alt) ? alt : nil
    if alt && !LOGGED[key]
      LOGGED[key] = true
      AshDroidLog.log("gfx #{key} -> #{res.inspect}")
    end
    res
  rescue StandardError
    nil
  end

  # PC / Steam Deck: no prebuilt images; the lettering is redrawn on the game's own
  # images as they load (ashdroid_art.rb + recipes in ashdroid_art.dat).
  module Live
    DATA_FILE = "ashdroid_art.dat"
    CONTROLS = "graphics/pictures/controls help/"
    @data = nil
    @cache = {}
    @busy = false

    def self.enabled?
      return @enabled unless @enabled.nil?
      @enabled = begin
        if AshDroidGfx::ES.empty? && AshDroidGfx::ANY.empty? && File.exist?(DATA_FILE) && File.exist?("ashdroid_art.rb")
          load File.expand_path("ashdroid_art.rb")
          @data = AshDroidArt.load_data(DATA_FILE)
          AshDroidLog.log("art: live, #{@data[:by_path].size} recipes, deck=#{ASHDROID_DECK.inspect}")
          true
        else
          false
        end
      rescue StandardError, ScriptError => e
        AshDroidLog.log("art: live disabled: #{e.class}: #{e.message}")
        false
      end
    end

    # Button names drawn in the art: the Deck's buttons, or the keyboard keys on PC
    def self.buttons
      ASHDROID_DECK ? { "USE" => "A", "BACK" => "B", "ACTION" => "X", "SPECIAL" => "R3" } :
                      { "USE" => "ENTER", "BACK" => "X", "ACTION" => "Z", "SPECIAL" => "D" }
    end

    # Which variant this image gets now: "es", "any", :controls or nil
    def self.variant(key)
      entries = @data[:by_path][key]
      if entries
        langs = entries.map { |e| e["lang"] || "es" }
        return "es" if AshDroidText.spanish? && langs.include?("es")
        # "any" images only change button names: nothing to do with keyboard keys
        return "any" if ASHDROID_DECK && langs.include?("any")
        return nil
      end
      return :controls if ASHDROID_DECK && key.start_with?(CONTROLS)
      nil
    end

    def self.busy?
      @busy
    end

    def self.to_img(bmp)
      w, h = bmp.width, bmp.height
      if bmp.respond_to?(:raw_data)
        AshDroidArt::Img.new(w, h, bmp.raw_data.unpack("N*"))
      else
        px = Array.new(w * h)
        h.times { |y| w.times { |x| c = bmp.get_pixel(x, y); px[y * w + x] = AshDroidArt.rgba(c.red.to_i, c.green.to_i, c.blue.to_i, c.alpha.to_i) } }
        AshDroidArt::Img.new(w, h, px)
      end
    end

    def self.write(bmp, img)
      if bmp.respond_to?(:raw_data=)
        bmp.raw_data = img.px.pack("N*")
      else
        img.h.times do |y|
          img.w.times do |x|
            c = img.px[y * img.w + x]
            bmp.set_pixel(x, y, Color.new(c >> 24, (c >> 16) & 255, (c >> 8) & 255, c & 255))
          end
        end
      end
    end

    # The user's own art (e.g. assets_E/Tipo_lucha_E.png), names compared without accents
    def self.user_image(rel)
      cur = "."
      rel.split("/").each do |part|
        want = AshDroidArt.strip_accents(part).downcase
        hit = (Dir.children(cur) rescue []).find { |n| AshDroidArt.strip_accents(n.dup.force_encoding("UTF-8")).downcase == want }
        return nil unless hit
        cur = File.join(cur, hit)
      end
      return nil unless File.file?(cur)
      @busy = true
      b = Bitmap.new(cur)
      img = to_img(b)
      b.dispose
      img
    rescue StandardError
      nil
    ensure
      @busy = false
    end

    # Called right after the game's image `path` was loaded into `bmp`
    def self.apply(bmp, path)
      return unless path.is_a?(String) && !@busy && enabled?
      key = AshDroidGfx.key_of(path)
      v = variant(key)
      return unless v
      ck = "#{key}|#{v}"
      img = @cache[ck]
      if img.nil?
        t = Time.now
        src = to_img(bmp)
        img = if v == :controls
                AshDroidArt::Controls.generate(File.basename(key), src, buttons, @data[:fonts]["5x7"])
              else
                AshDroidArt.relabel(src, @data[:by_path][key], v, @data[:fonts], buttons, method(:user_image))
              end
        @cache[ck] = img || false
        AshDroidLog.log("gfx live #{ck} #{img ? 'ok' : 'skip'} #{((Time.now - t) * 1000).round}ms")
      end
      write(bmp, img) if img && img.w == bmp.width && img.h == bmp.height
    rescue StandardError => e
      @cache[ck] = false if ck
      AshDroidLog.log("gfx live error #{path}: #{e.class}: #{e.message}")
    end
  end
end

# Credit line added to the start-up splash (the Pokémon Essentials one), in the game's language
module AshDroidCredits
  LINES = [[/spanish/i, "Parche Ash Droid: Alberto (afsenovilla)"],
           [/french/i, "Patch Ash Droid : Alberto (afsenovilla)"],
           [/german/i, "Ash-Droid-Patch: Alberto (afsenovilla)"]]
  DEFAULT = "Ash Droid patch: Alberto (afsenovilla)"

  def self.stamp(bmp, path)
    return unless path =~ /Titles[\/\\]splash1(\.\w+)?\z/i
    f = AshDroidText.lang_file.to_s
    text = (LINES.find { |re, _| f =~ re } || [nil, DEFAULT])[1]
    # The game's own pixel font (the default bitmap font isn't set up yet at this point)
    begin
      pbSetSystemFont(bmp)
    rescue StandardError, NameError
      bmp.font.name = ["Power Green", "Power Clear"]
      bmp.font.size = 29
    end
    bmp.font.color = Color.new(255, 255, 255)
    bmp.draw_text(0, bmp.height - 56, bmp.width, 32, text, 1)
  rescue StandardError => e
    AshDroidLog.log("credits stamp error: #{e.class}: #{e.message}")
  end
end

begin
  class Bitmap
    alias_method :__ashdroid_initialize, :initialize
    def initialize(*args)
      if args.length == 1 && (alt = AshDroidGfx.redirect(args[0]))
        args = [alt]
      end
      __ashdroid_initialize(*args)
      if args.length == 1 && args[0].is_a?(String)
        AshDroidGfx::Live.apply(self, args[0])
        AshDroidCredits.stamp(self, args[0])
      end
    end

    # The native Linux engine (Steam Deck) reports a text height of about 40% of the
    # font size; Essentials then draws the text in a box that short and its lower half
    # is cut off ("Nueva partida" -> "Nuaua nartida"). Report the font size instead.
    alias_method :__ashdroid_text_size, :text_size
    def text_size(str)
      r = __ashdroid_text_size(str)
      fs = font.size.to_i
      r.height = fs if fs > 0 && r.height < fs * 0.6
      r
    rescue StandardError
      __ashdroid_text_size(str)
    end

    # Accented capitals (Á É Í Ó Ú Ñ Ü) rise above the font's ascent; the text
    # surface grows at the top and mkxp centres it vertically, so those letters
    # (or the whole line containing them) end up a couple of pixels too low.
    # Move them back up by half the extra height.
    ASHDROID_TALL = /[ÁÉÍÓÚÑÜÀÈÌÒÙÂÊÎÔÛÄËÏÖ]/
    alias_method :__ashdroid_draw_text, :draw_text
    def draw_text(*args)
      begin
        text = args.length >= 5 ? args[4] : args[1]
        if text.is_a?(String)
          # Steam Deck with the game's own fonts (installed before the fixed copies existed):
          # the engine centres the text on a line height that is far too short, so it sits low.
          d = ASHDROID_DECK ? AshDroidFixes.deck_shift(self) : 0
          d += AshDroidFixes.tall_shift(self, text) if text =~ ASHDROID_TALL
          if d != 0
            if args.length >= 5
              args = args.dup
              args[1] -= d
            elsif args[0].is_a?(Rect)
              r = args[0]
              args = [r.x, r.y - d, r.width, r.height] + args[1..-1]
            end
          end
        end
      rescue StandardError
      end
      __ashdroid_draw_text(*args)
    end
  end
rescue StandardError, NameError
end

# Wrap _INTL / _ISPRINTF as soon as the game defines them (game scripts load
# after this file, so they can't be wrapped directly).
begin
  class Object
    ASHDROID_WRAP = [:_INTL, :_ISPRINTF, :_MAPINTL, :_MAPISPRINTF]

    def self.method_added(name)
      super
      if self == Object && name == :pbSetResizeFactor && !@__ashdroid_wrapping && !ASHDROID_PC
        # Window sizes make no sense on a handheld: always fill the screen
        # (the game's "screen size" option would otherwise shrink it to a window).
        @__ashdroid_wrapping = true
        begin
          orig = instance_method(name)
          define_method(name) do |factor|
            orig.bind(self).call(-1)
          end
          private name
          AshDroidLog.log("pbSetResizeFactor forced to fullscreen")
        ensure
          @__ashdroid_wrapping = false
        end
        return
      end
      if self == Object && name == :pbDrawTextPositions && !@__ashdroid_wrapping
        @__ashdroid_wrapping = true
        begin
          orig = instance_method(name)
          define_method(name) do |bitmap, textpos, *args, &blk|
            orig.bind(self).call(bitmap, AshDroidFixes.fit_textpos(bitmap, textpos), *args, &blk)
          end
          private name
        ensure
          @__ashdroid_wrapping = false
        end
        return
      end
      if self == Object && name == :pbConfirmMessage && !@__ashdroid_wrapping
        # Remember when the player confirmed "Quit Game": the save menu shown next
        # then says "Exit" instead of "Cancel" (cancelling there quits the game).
        @__ashdroid_wrapping = true
        begin
          orig = instance_method(name)
          define_method(name) do |message, *args, &blk|
            message = AshDroidText.translate(message) if message.is_a?(String)
            r = orig.bind(self).call(message, *args, &blk)
            $ashdroid_quitting = true if r && message.to_s =~ /quit the game|salir del juego/i
            r
          end
          private name
        ensure
          @__ashdroid_wrapping = false
        end
        return
      end
      if self == Object && name == :pbMessage && !@__ashdroid_wrapping
        # Skip PC-only notices (e.g. "...in the Helpful Notes folder": that folder
        # is part of the PC download, not of the game on this device).
        @__ashdroid_wrapping = true
        begin
          orig = instance_method(name)
          define_method(name) do |message, *args, &blk|
            next 0 if !ASHDROID_PC && message.is_a?(String) && message =~ AshDroidText::SKIP_MESSAGES
            orig.bind(self).call(message, *args, &blk)
          end
          private name
        ensure
          @__ashdroid_wrapping = false
        end
        return
      end
      return unless self == Object && ASHDROID_WRAP.include?(name)
      return if @__ashdroid_wrapping
      AshDroidLog.log("wrapping #{name}")
      @__ashdroid_wrapping = true
      begin
        orig = instance_method(name)
        key_index = name.to_s.start_with?("_MAP") ? 1 : 0
        define_method(name) do |*args, &blk|
          if $ashdroid_quitting && name == :_INTL && args[0] == "Cancel"
            f = AshDroidText.lang_file.to_s
            next(f =~ /spanish/i ? "Salir" : f =~ /french/i ? "Quitter" : f =~ /german/i ? "Beenden" : "Quit")
          end
          es = AshDroidText.spain_lookup(args[key_index], args[key_index + 1])
          if es && es =~ /\A[¡¿]?\{1\}/ && args[key_index + 1].is_a?(String) &&
             args[key_index + 1] =~ /\A[a-záéíóúñ]/
            # "{1}" can be "el Pidgey salvaje": capitalise it at the start of a sentence
            args = args.dup
            args[key_index + 1] = args[key_index + 1].sub(/\A./) { |c| c.upcase }
          end
          es ||= AshDroidText.lookup(args[key_index])
          if es
            args = args.dup
            args[key_index] = es
          end
          AshDroidText.fix(orig.bind(self).call(*args, &blk))
        end
        private name
      ensure
        @__ashdroid_wrapping = false
      end
    end
  end
rescue StandardError
  # Never break the game because of a cosmetic patch
end

# Spanish word order fixes in game classes, applied as each class is defined
# (game scripts load after this file).
module AshDroidFixes
  # Battle ability splash: English draws "Ho-Oh's" over "Pressure"; with the
  # Spanish "{1}'s" => "de {1}" that reads "de Ho-Oh / Presión". In Spanish put
  # the ability first: "Presión / de Ho-Oh".
  module AbilitySplashBarES
    def refresh
      return super unless AshDroidText.spanish?
      self.bitmap.clear
      return if !@battler
      textX = (@side == 0) ? 10 : self.bitmap.width - 8
      ability = @ability.is_a?(String) ? @ability : @battler.abilityName
      textPos = [
        [ability, textX, -4, @side == 1, self.class::TEXT_BASE_COLOR, self.class::TEXT_SHADOW_COLOR, true],
        [_INTL("{1}'s", @battler.name), textX, 32, @side == 1,
         self.class::TEXT_BASE_COLOR, self.class::TEXT_SHADOW_COLOR, true],
      ]
      pbDrawTextPositions(self.bitmap, textPos)
    rescue StandardError
      super
    end
  end

  # Trainer names: Spanish puts possessive classes after the name
  # ("Jessie y James del Team Rocket"), other classes before ("Cazabichos Pedro").
  module TrainerES
    def full_name
      return super unless AshDroidText.spain? || AshDroidText.lang_file.to_s =~ /french/i
      type = trainer_type_name.to_s
      return @name.to_s if type.empty?
      type =~ /\A(del |de la |de los |de las |de |du |des |de l'|d')/i ? "#{@name} #{type}" : "#{type} #{@name}"
    rescue StandardError
      super
    end
  end

  # Options menu: drop "Screen Size" (the game always fills the device screen)
  # Options screen: drop "Screen Size" on the device (it only makes a window
  # smaller) and, while the game is in Spanish, offer the Spanish music.
  module OptionsAshDroid
    def pbAddOnOptions(options)
      opts = super
      unless ASHDROID_PC
        names = ["Screen Size", (_INTL("Screen Size") rescue "Screen Size")]
        opts = opts.reject { |o| o.respond_to?(:name) && names.include?(o.name) }
      end
      if AshDroidAudio.available? && defined?(EnumOption) && (l = AshDroidAudio.lang)
        opts = opts + [EnumOption.new(l[2], l[3],
          proc { AshDroidAudio.enabled? ? 1 : 0 },
          proc { |v| AshDroidAudio.enabled = (v == 1) })]
      end
      opts
    rescue StandardError => e
      AshDroidLog.log("options hook error: #{e.class}: #{e.message}")
      super
    end
  end

  # Leaving the save screen ends the "quitting" context
  module HardcodedTextWindow
    def text=(value)
      super(AshDroidText.hardcoded(value))
    end
  end

  module SaveScreenQuit
    def pbSaveScreen
      super
    ensure
      $ashdroid_quitting = false
    end
  end

  # Battle menu: after a switch (chosen, forced, or after a faint) the new Pokémon's
  # command menu opens on FIGHT instead of staying on the POKéMON button.
  # The scene remembers the last command per battler slot (@lastCmd); the game
  # already resets the move cursor when a Pokémon comes in, so reset this one too.
  module BattleFightAfterSwitch
    def pbResetMoveIndex(idxBattler)
      super if defined?(super)
    ensure
      AshDroidFixes.reset_command(self, idxBattler)
    end

    def pbSendOutBattlers(sendOuts, *args)
      super
    ensure
      (sendOuts || []).each { |s| AshDroidFixes.reset_command(self, s[0]) rescue nil }
    end
  end

  def self.reset_command(scene, idxBattler)
    cmds = scene.instance_variable_get(:@lastCmd)
    cmds[idxBattler] = 0 if cmds.is_a?(Array) && idxBattler.is_a?(Integer)
  rescue StandardError
  end

  # The game's "Spanish" entry becomes "Spanish (Spain)" (same data file), so the
  # Spain retranslation replaces the old Spanish instead of being an extra language.
  def self.add_language(settings)
    langs = settings::LANGUAGES
    return if langs.any? { |l| l[0] == AshDroidText::SPAIN_NAME }
    idx = langs.index { |l| l[1].to_s =~ /spanish|espa/i }
    return unless idx
    entry = [AshDroidText::SPAIN_NAME, langs[idx][1]]
    if langs.frozen? || langs[idx].frozen?
      list = langs.map(&:dup)
      list[idx] = entry
      settings.send(:remove_const, :LANGUAGES)
      settings.const_set(:LANGUAGES, list.freeze)
    else
      langs[idx] = entry
    end
    @spain_index = idx
    AshDroidLog.log("language replaced: #{entry.inspect} at #{idx}")
  rescue StandardError => e
    AshDroidLog.log("add_language error: #{e.class}: #{e.message}")
  end

  def self.spain_index
    @spain_index
  end

  # Two texts on the same line, one left-aligned and one right-aligned: if a translation makes
  # them overlap, the left one (a label such as "Continuar" next to the location on the load
  # screen) is left out, so the right one is read whole.
  def self.fit_textpos(bitmap, textpos)
    return textpos unless bitmap && textpos.is_a?(Array) && textpos.length > 1
    out = textpos.map { |t| t.is_a?(Array) ? t.dup : t }
    rows = out.select { |t| t.is_a?(Array) && t[0].is_a?(String) && t[1].is_a?(Numeric) && t[2].is_a?(Numeric) }
    rows.each do |r|
      next unless r[3] == 1 || r[3] == true
      rows.each do |l|
        next if l.equal?(r) || l[0].empty? || !(l[3].nil? || l[3] == 0 || l[3] == false)
        next unless (l[2] - r[2]).abs < 8 && l[1] < r[1]
        l[0] = "" if l[1] + bitmap.text_size(l[0]).width + 8 > r[1] - bitmap.text_size(r[0]).width
      end
    end
    out
  rescue StandardError
    textpos
  end

  # Accented capitals (É, Ú…) rise above the font's ascent. Engines that size the text
  # surface to its contents make it taller and centre it, so the line holding one sinks a
  # little (and a lone "É" in a message, drawn letter by letter, sits lower than "ter").
  # Measured once per font by drawing "E" and "É" and comparing where their ink ends:
  # 0 where the engine doesn't do that.
  def self.tall_shift(bitmap, text)
    f = bitmap.font
    key = [f.name.to_s, f.size, f.bold]
    @tall ||= {}
    return @tall[key] if @tall.key?(key)
    @tall[key] = 0
    h = f.size.to_i + 3
    w = f.size.to_i * 2
    bottoms = ["E", "É"].map do |ch|
      b = Bitmap.new(w, h)
      b.font.name = f.name
      b.font.size = f.size
      b.font.bold = f.bold
      b.font.color = Color.new(255, 255, 255)
      b.__ashdroid_draw_text(0, 0, w, h, ch)
      y = (h - 1).downto(0).find { |yy| (0...w).any? { |x| b.get_pixel(x, yy).alpha > 0 } }
      b.dispose
      y
    end
    d = bottoms.all? ? bottoms[1] - bottoms[0] : 0
    d = 0 if d < 0 || d > h / 3
    AshDroidLog.log("tall text #{key.inspect}: E=#{bottoms[0].inspect} É=#{bottoms[1].inspect} shift=#{d}")
    @tall[key] = d
  rescue StandardError
    0
  end

  # Upward shift for the Steam Deck when its engine still reports a line height of ~40% of the
  # font size (game's own fonts, see tools/linux/install.py): half the missing height.
  def self.deck_shift(bitmap)
    f = bitmap.font
    key = [f.name.to_s, f.size]
    @deck ||= {}
    return @deck[key] if @deck.key?(key)
    fs = f.size.to_i
    h = bitmap.__ashdroid_text_size("A").height
    @deck[key] = h > 0 && h < fs * 0.6 ? [(fs - h) / 4, 4].min : 0
  rescue StandardError
    0
  end

  # Saves made with earlier versions of the patch may point to the old extra
  # "Español (España)" entry (after the last language): use Spanish (Spain).
  module PokemonSystemLang
    def language
      v = super
      n = (Settings::LANGUAGES.size rescue nil)
      if v.is_a?(Integer) && n && v >= n && AshDroidFixes.spain_index
        v = AshDroidFixes.spain_index
        @language = v
      end
      v
    rescue StandardError
      super
    end
  end

  # Game data texts (item/move/ability names and descriptions…) come from the
  # language file by numeric id; the Spain pack overrides them as "@<type>:<id>".
  module MessageTypesES
    def get(type, id)
      pk = AshDroidText.retrans_pack
      if !pk.empty?
        r = pk["@#{type}:#{id}"]
        return r if r
      elsif AshDroidText.english?
        r = AshDroidText.en_pack["@#{type}:#{id}"]
        return r if r
      end
      super
    end

    # Texts looked up by their English (e.g. trainer names): "@<type>:<English>"
    def getFromHash(type, key)
      if key.is_a?(String) && !(pk = AshDroidText.retrans_pack).empty?
        r = pk["@#{type}:#{AshDroidText.norm(key)}"]
        return r if r
      end
      super
    end
  end

  def self.hook_message_types(mod)
    return if mod.singleton_class.ancestors.include?(MessageTypesES)
    return unless mod.respond_to?(:get)
    mod.singleton_class.prepend(MessageTypesES)
    AshDroidLog.log("fix applied: MessageTypes")
  rescue StandardError => e
    AshDroidLog.log("MessageTypes hook error: #{e.class}: #{e.message}")
  end

  HOOKS = {
    "MessageTypes" => ->(m) { AshDroidFixes.hook_message_types(m) },
    "AbilitySplashBar" => AbilitySplashBarES,
    "Trainer" => TrainerES,
    "PokemonOption_Scene" => OptionsAshDroid,
    "PokemonSaveScreen" => SaveScreenQuit,
    "Window_AdvancedTextPokemon" => HardcodedTextWindow,
    "PokeBattle_Scene" => BattleFightAfterSwitch, # Essentials v19
    "Battle::Scene" => BattleFightAfterSwitch,    # Essentials v20+
    "PokemonSystem" => PokemonSystemLang,
    "Settings" => ->(m) { AshDroidFixes.add_language(m) if m.const_defined?(:LANGUAGES, false) },
  }

  begin
    @tp = TracePoint.new(:end) do |tp|
      begin
        mod = tp.self
        hook = mod.is_a?(Module) ? HOOKS[mod.name] : nil
        if hook.is_a?(Proc)
          hook.call(mod)
        elsif hook && mod.is_a?(Class) && !mod.ancestors.include?(hook)
          mod.prepend(hook)
          AshDroidLog.log("fix applied: #{mod.name}")
        end
      rescue StandardError
      end
    end
    @tp.enable
  rescue StandardError
  end
end

# Music substitutions (by file name, any folder/extension): play the track on
# the right instead of the one on the left.
module AshDroidAudio
  ALIASES = { "teamrocket1" => "TeamRocket" }

  # Songs in the game's language (Spanish, French, German intro…), bundled with the
  # patch in ashdroid_audio_<code>/<same file name>. On by default; switched from the
  # game's Options ("Música en castellano" / "Musique en français" / "Musik auf Deutsch")
  # while the game is in that language.
  LANGS = [[/spanish/i, "es", "Música en castellano", ["No", "Sí"]],
           [/french/i, "fr", "Musique en français", ["Non", "Oui"]],
           [/german/i, "de", "Musik auf Deutsch", ["Aus", "An"]]]
  SETTINGS = "ashdroid_settings.txt"

  def self.lang
    f = AshDroidText.lang_file.to_s
    LANGS.find { |re, *| f =~ re }
  end

  def self.dir
    l = lang
    l ? "ashdroid_audio_#{l[1]}" : nil
  end

  def self.available?
    d = dir
    d && File.directory?(d) && !Dir.children(d).empty?
  rescue StandardError
    false
  end

  def self.settings
    @settings ||= (File.exist?(SETTINGS) ? File.read(SETTINGS).scan(/^(\w+)=(\d)$/).to_h : {})
  rescue StandardError
    @settings = {}
  end

  def self.enabled?
    l = lang
    !!(l && settings["music_#{l[1]}"] != "0")
  end

  def self.enabled=(on)
    l = lang
    return unless l
    settings["music_#{l[1]}"] = on ? "1" : "0"
    File.write(SETTINGS, settings.map { |k, v| "#{k}=#{v}\n" }.join)
  rescue StandardError
  end

  def self.map(name)
    return name unless name.is_a?(String)
    base = File.basename(name).sub(/\.[^.]+\z/, "")
    alt = ALIASES[base.downcase]
    if alt
      name = File.join(File.dirname(name), alt)
      base = alt
    end
    d = dir
    if d && enabled? && File.directory?(d)
      hit = Dir.children(d).find { |f| f.sub(/\.[^.]+\z/, "").casecmp?(base) }
      if hit
        AshDroidLog.log("audio #{d}: #{base} -> #{hit}")
        return File.join(d, hit)
      end
    end
    name
  rescue StandardError
    name
  end
end

begin
  module Audio
    class << self
      alias_method :__ashdroid_bgm_play, :bgm_play
      def bgm_play(name, *args)
        __ashdroid_bgm_play(AshDroidAudio.map(name), *args)
      end
    end
  end
rescue StandardError, NameError
end

# Steam Deck: the ☰ button (START) also works as B (open the pause menu, cancel),
# like "B / START" on Android. mkxp-z leaves START unbound by default.
if ASHDROID_DECK
  module AshDroidDeck
    def self.start?(kind)
      return false unless defined?(Input::Controller) && Input::Controller.respond_to?(kind)
      Input::Controller.send(kind, :START) ? true : false
    rescue StandardError
      false
    end
  end

  begin
    module Input
      class << self
        alias_method :__ashdroid_trigger, :trigger?
        alias_method :__ashdroid_press, :press?
        alias_method :__ashdroid_repeat, :repeat?
        def trigger?(b)
          __ashdroid_trigger(b) || (b == Input::B && AshDroidDeck.start?(:triggerex?))
        end
        def press?(b)
          __ashdroid_press(b) || (b == Input::B && AshDroidDeck.start?(:pressex?))
        end
        def repeat?(b)
          __ashdroid_repeat(b) || (b == Input::B && AshDroidDeck.start?(:repeatex?))
        end
      end
    end
    AshDroidLog.log("deck: START works as B")
  rescue StandardError, NameError => e
    AshDroidLog.log("deck: START hook failed: #{e.class}: #{e.message}")
  end
end
