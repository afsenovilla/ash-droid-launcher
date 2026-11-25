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
# ASHDROID_DEVICE=pc|deck|lcd|oled|handheld in the launch options overrides the detection.
# "handheld" = Anbernic H700 consoles and similar (Knulli...) running the native Linux engine of the
# handheld package: it is started with that value, or recognised by the ashdroid_handheld.txt marker
# the installer leaves in the game folder.
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
    return "handheld" if forced == "handheld"
    return "handheld" if forced.empty? && File.exist?("ashdroid_handheld.txt")
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

# Handheld consoles (H700): same actions as the Deck, but without stick clicks: the L2 / R2 triggers
# do what L3 / R3 do (see the hook at the end). The names of A/B/X/Y depend on how the firmware maps
# the console's pad: ashdroid_handheld.txt has "layout=swap" when the labels printed on the console
# are the other way round (confirm = the button labelled B), and nothing or "layout=same" otherwise.
module AshDroidHandheld
  SWAP = { "A" => "B", "B" => "A", "X" => "Y", "Y" => "X" }
  def self.config
    @config ||= begin
      File.read("ashdroid_handheld.txt").to_s
    rescue StandardError
      ""
    end
  end

  def self.swap?
    config =~ /layout\s*=\s*swap/i ? true : false
  end

  # "B / START" -> "A / START" when the console's labels are swapped
  def self.label(s)
    swap? ? s.gsub(/\b[ABXY]\b/) { |b| SWAP[b] } : s
  end
end
ASHDROID_HANDHELD = ASHDROID_DECK == "handheld"
if ASHDROID_HANDHELD
  ASHDROID_KEYS.merge!("D" => "R2", "S" => "L2")
  ASHDROID_KEYS.each_key { |k| ASHDROID_KEYS[k] = AshDroidHandheld.label(ASHDROID_KEYS[k]) }
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
AshDroidLog.log("max texture size=#{(Bitmap.max_size rescue '?')}")

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
  RETRANS = [[/spanish/i, SPAIN_PACK_FILE], [/french/i, "ashdroid_fr.dat"], [/german/i, "ashdroid_de.dat"],
             [/italian/i, "ashdroid_it.dat"]]

  # Italian isn't one of the game's languages: the patch adds "Italiano", which loads the
  # game's English file with every text replaced by the Italian pack. For the patch it
  # counts as the (virtual) language file "italian.dat".
  ITALIAN_NAME = "Italiano"
  ITALIAN_FILE = "italian.dat"

  def self.lang_file
    langs = Settings::LANGUAGES
    idx = $PokemonSystem ? $PokemonSystem.language : nil
    return nil unless langs && idx && langs[idx]
    langs[idx][0] == ITALIAN_NAME ? ITALIAN_FILE : langs[idx][1].to_s
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
    !!(lang_file.to_s =~ /english/i)
  rescue StandardError, NameError
    false
  end

  # Language of the redrawn game art ("es", "fr", "de") or nil (English: originals)
  def self.art_lang
    return "es" if spanish?
    f = lang_file.to_s
    return "fr" if f =~ /french/i
    return "de" if f =~ /german/i
    return "it" if f =~ /italian/i
    nil
  rescue StandardError
    nil
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
      if ASHDROID_HANDHELD
        es ? "Los botones de esta consola se cambian en el menú de mandos de su sistema." :
             "On this console, change the buttons in its system's controller settings."
      elsif ASHDROID_DECK
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
      if ASHDROID_HANDHELD
        es ? "Para hacer capturas usa el atajo de captura de pantalla de la consola." :
             "To take screenshots, use your console's screenshot shortcut."
      elsif ASHDROID_DECK
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

# Translated versions of images with English words drawn in them (generated by the
# launcher into ashdroid_art/ from the game's own art). Each language's images are
# used only while the game is in that language; the original files are untouched.
module AshDroidGfx
  # Images redrawn by the launcher: LANG["es"/"fr"/"de"] replace the originals only
  # while the game is in that language; ANY ones (button names only) otherwise.
  LANG = Hash.new { |h, k| h[k] = {} }
  ES = LANG["es"]
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
        (lang == "any" ? ANY : LANG[lang])[key] = file
      end
    end
    AshDroidLog.log("art: #{INDEX} #{LANG.map { |l, m| "#{l}=#{m.size}" }.join(' ')} any=#{ANY.size}") if File.exist?(INDEX)
  rescue StandardError
  end

  def self.key_of(path)
    path.tr("\\", "/").sub(/\A\.\//, "").sub(/\.png\z/i, "").downcase
  end

  def self.redirect(path)
    return nil unless path.is_a?(String)
    key = key_of(path)
    lang = AshDroidText.art_lang
    alt = (lang && LANG.key?(lang) && LANG[lang][key]) || ANY[key]
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
        if AshDroidGfx::LANG.values.all?(&:empty?) && AshDroidGfx::ANY.empty? && File.exist?(DATA_FILE) && File.exist?("ashdroid_art.rb")
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
      ASHDROID_HANDHELD ? { "USE" => AshDroidHandheld.label("A"), "BACK" => AshDroidHandheld.label("B"),
                            "ACTION" => AshDroidHandheld.label("X"), "SPECIAL" => "R2" } :
      ASHDROID_DECK ? { "USE" => "A", "BACK" => "B", "ACTION" => "X", "SPECIAL" => "R3" } :
                      { "USE" => "ENTER", "BACK" => "X", "ACTION" => "Z", "SPECIAL" => "D" }
    end

    # Which variant this image gets now: "es"/"fr"/"de", "any", :controls or nil
    def self.variant(key)
      entries = @data[:by_path][key]
      if entries
        langs = entries.map { |e| e["lang"] || "es" }
        lang = AshDroidText.art_lang
        return lang if lang && langs.include?(lang)
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

    # The game's TrueType font Fonts/<name>.ttf as an alpha mask (see ArtEngine.Host#ttf):
    # [w, h, floats 0..1], or nil if the font is not there.
    def self.ttf(name, size, text)
      fam = name.split(" ").map(&:capitalize).join(" ").gsub(" And ", " and ")
      return nil unless !Font.respond_to?(:exist?) || Font.exist?(fam)
      w = (size * 0.8 * [1, text.size].max).to_i + size * 2
      h = size * 2
      @busy = true
      b = Bitmap.new(w, h)
      b.font = Font.new(fam, size)
      b.font.bold = false
      b.font.italic = false
      b.font.outline = false if b.font.respond_to?(:outline=)
      b.font.shadow = false if b.font.respond_to?(:shadow=)
      b.font.color = Color.new(255, 255, 255, 255)
      b.draw_text(size / 2, 0, w - size / 2, h, text, 0)
      a = if b.respond_to?(:raw_data)
            b.raw_data.unpack("N*").map { |c| (c & 255) / 255.0 }
          else
            px = Array.new(w * h)
            h.times { |y| w.times { |x| px[y * w + x] = b.get_pixel(x, y).alpha / 255.0 } }
            px
          end
      b.dispose
      [w, h, a]
    rescue StandardError => e
      AshDroidLog.log("gfx ttf #{name}: #{e.class}: #{e.message}")
      nil
    ensure
      @busy = false
    end

    # Redrawing an image takes a while on a slow CPU (seconds on the H700 consoles), so each result is
    # kept as a PNG in ashdroid_artcache/ and reused from then on, also in later sessions. The file
    # name carries the recipes' version and the button names, so a new patch or another layout
    # makes new ones.
    CACHE_DIR = "ashdroid_artcache"

    def self.disk_name(ck)
      @stamp ||= begin
        st = File.exist?(DATA_FILE) ? "#{File.size(DATA_FILE)}-#{File.mtime(DATA_FILE).to_i}" : "0"
        (st + "|" + buttons.sort.join(",")).unpack("C*").each_with_index.inject(7) { |h, (c, i)| (h * 31 + c + i) % 2_147_483_647 }.to_s(36)
      end
      File.join(CACHE_DIR, ck.gsub(/[^a-z0-9._-]+/i, "_") + "-" + @stamp)
    end

    def self.from_disk(bmp, ck)
      f = disk_name(ck)
      if File.exist?(f + ".skip")
        @cache[ck] = false
        return true
      end
      return false unless File.exist?(f + ".png")
      @busy = true
      b = Bitmap.new(f + ".png")
      ok = b.width == bmp.width && b.height == bmp.height
      if ok
        if bmp.respond_to?(:raw_data=) && b.respond_to?(:raw_data)
          bmp.raw_data = b.raw_data
        else
          bmp.clear
          bmp.blt(0, 0, b, b.rect)
        end
      end
      b.dispose
      ok
    rescue StandardError
      false
    ensure
      @busy = false
    end

    def self.to_disk(bmp, ck)
      Dir.mkdir(CACHE_DIR) unless File.directory?(CACHE_DIR)
      f = disk_name(ck)
      if bmp
        bmp.to_file(f + ".png") if bmp.respond_to?(:to_file)
      else
        File.write(f + ".skip", "")
      end
    rescue StandardError => e
      AshDroidLog.log("gfx cache: #{e.class}: #{e.message}")
    end

    # Called right after the game's image `path` was loaded into `bmp`
    def self.apply(bmp, path)
      return unless path.is_a?(String) && !@busy && enabled?
      key = AshDroidGfx.key_of(path)
      v = variant(key)
      return unless v
      ck = "#{key}|#{v}"
      img = @cache[ck]
      return if img.nil? && from_disk(bmp, ck)
      if img.nil?
        t = Time.now
        src = to_img(bmp)
        img = if v == :controls
                AshDroidArt::Controls.generate(File.basename(key), src, buttons, @data[:fonts]["5x7"])
              else
                AshDroidArt.relabel(src, @data[:by_path][key], v, @data[:fonts], buttons, method(:user_image), method(:ttf))
              end
        @cache[ck] = img || false
        AshDroidLog.log("gfx live #{ck} #{img ? 'ok' : 'skip'} #{((Time.now - t) * 1000).round}ms")
        if img && img.w == bmp.width && img.h == bmp.height
          write(bmp, img)
          to_disk(bmp, ck)
          return
        end
        to_disk(nil, ck) unless img
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
           [/german/i, "Ash-Droid-Patch: Alberto (afsenovilla)"],
           [/italian/i, "Patch Ash Droid: Alberto (afsenovilla)"]]
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
    # Any non-ASCII text may hold an accented capital (Á, Ã, Ö, Ñ…): tall_shift checks the letters
    ASHDROID_TALL = /[^\x00-\x7F]/
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
            next(f =~ /spanish/i ? "Salir" : f =~ /french/i ? "Quitter" : f =~ /german/i ? "Beenden" : f =~ /italian/i ? "Esci" : "Quit")
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

# Second-screen layouts (BETA). Pure layout code: each method takes plain data gathered from the game and
# returns the screen as a list of drawing operations for the launcher (DsScene.java), in a 1240x1080 space.
# The layouts are the ones approved as mock-ups in docs/ds-screen (same coordinates, same rules): the game's
# own images enlarged by whole factors and widened only by repeating uniform lines, texts in the game's
# font at multiples of 13 px, and buttons drawn by code in "art pixels" of 6 screen pixels.
module AshDroidDSUI
  W = 1240
  H = 1080
  S = 6
  WHITE = [248, 248, 248].freeze
  DARK = [40, 40, 40].freeze
  INK = [64, 64, 80].freeze
  INK_SH = [168, 168, 192].freeze
  BLUE = [56, 136, 232].freeze
  GREEN = [88, 200, 88].freeze
  ORANGE = [240, 168, 48].freeze
  TEAL = [64, 184, 184].freeze
  PINK = [248, 144, 200].freeze
  PURPLE = [160, 112, 232].freeze
  RED = [232, 96, 96].freeze
  GREY = [168, 168, 196].freeze
  LAVENDER_SHADOW = [124, 124, 168].freeze
  PARTY_SHADOW = [95, 151, 151].freeze
  FOOT_SHADOW = [25, 69, 72].freeze
  HOUSE = [".....#.....", "....###....", "...#####...", "..#######..", ".#########.", "###########",
           ".#########.", ".###...###.", ".###...###.", ".###...###."].freeze
  TYPE_COLORS = {
    "NORMAL" => [168, 168, 120], "FIRE" => [240, 128, 48], "WATER" => [104, 144, 240],
    "ELECTRIC" => [248, 208, 48], "GRASS" => [120, 200, 80], "ICE" => [152, 216, 216],
    "FIGHTING" => [192, 48, 40], "POISON" => [160, 64, 160], "GROUND" => [224, 192, 104],
    "FLYING" => [168, 144, 240], "PSYCHIC" => [248, 88, 136], "BUG" => [168, 184, 32],
    "ROCK" => [184, 160, 56], "GHOST" => [112, 88, 152], "DRAGON" => [112, 56, 248],
    "DARK" => [112, 88, 72], "STEEL" => [184, 184, 208], "FAIRY" => [238, 153, 172]
  }.freeze
  # Words the game has no text for. Official terms.
  LABELS = {
    "en" => { back: "BACK", wild: "WILD POKéMON", here: "You are here", unseen: "??? = not seen", lv: "Lv",
              wild_title: "Wild Pokémon", card: "Card %d of %d", save_q: "Save the game?", saved: "Game saved",
              save: "SAVE", saved_button: "SAVED", held: "Held: %s", no_held: "No held item", type: "Type:", item: "Item:", power: "Power",
              accuracy: "Accuracy", pages: %w[INFO MEMO SKILLS MOVES RIBBONS], sum_help: "Details are shown above.",
              drop: "Let go to swap.", grass: "GRASS", fishing: "FISHING", cave: "CAVE", change: "SWITCH", mega: "MEGA",
              eff: ["No effect", "Not very effective", "Super effective"], badges: "Badges:", pokedex: "Pokédex:",
              time: "Time:", money: "Money:", started: "Started:", no_wild: "No wild Pokémon here.",
              busy: "", choose: "Choose a Pokémon." },
    "es" => { back: "VOLVER", wild: "POKéMON SALVAJES", here: "Estás aquí", unseen: "??? = sin ver", lv: "Nv",
              wild_title: "Pokémon salvajes", card: "Tarjeta %d de %d", save_q: "¿Guardar la partida?",
              saved: "Partida guardada", save: "GUARDAR", saved_button: "GUARDADO", held: "Equipado: %s", no_held: "Sin objeto equipado",
              type: "Tipo:", item: "Objeto:", power: "Potencia", accuracy: "Precisión",
              pages: %w[INFORMACIÓN NOTAS HABILIDADES MOVIMIENTOS CINTAS], sum_help: "Los datos se ven arriba.",
              drop: "Suelta para cambiar.", grass: "HIERBA", fishing: "PESCA", cave: "CUEVA", change: "CAMBIAR", mega: "MEGA",
              eff: ["No afecta", "Poco eficaz", "Muy eficaz"], badges: "Medallas:", pokedex: "Pokédex:",
              time: "Tiempo:", money: "Dinero:", started: "Inicio:", no_wild: "Aquí no hay Pokémon salvajes.",
              busy: "", choose: "Elige un Pokémon." },
    "fr" => { back: "RETOUR", wild: "POKéMON SAUVAGES", here: "Vous êtes ici", unseen: "??? = non vu", lv: "N.",
              wild_title: "Pokémon sauvages", card: "Carte %d sur %d", save_q: "Sauvegarder la partie ?",
              saved: "Partie sauvegardée", save: "SAUVEGARDER", saved_button: "SAUVEGARDÉ", held: "Tenu : %s", no_held: "Aucun objet tenu",
              type: "Type :", item: "Objet :", power: "Puissance", accuracy: "Précision",
              pages: %w[INFOS NOTES STATS CAPACITÉS RUBANS], sum_help: "Les données sont en haut.",
              drop: "Relâchez pour échanger.", grass: "HERBE", fishing: "PÊCHE", cave: "GROTTE", change: "CHANGER", mega: "MÉGA",
              eff: ["Sans effet", "Peu efficace", "Super efficace"], badges: "Badges :", pokedex: "Pokédex :",
              time: "Temps :", money: "Argent :", started: "Début :", no_wild: "Aucun Pokémon sauvage ici.",
              busy: "", choose: "Choisissez un Pokémon." },
    "de" => { back: "ZURÜCK", wild: "WILDE POKéMON", here: "Du bist hier", unseen: "??? = nicht gesehen", lv: "Lv.",
              wild_title: "Wilde Pokémon", card: "Karte %d von %d", save_q: "Spiel speichern?",
              saved: "Spiel gespeichert", save: "SPEICHERN", saved_button: "GESPEICHERT", held: "Trägt: %s", no_held: "Trägt kein Item",
              type: "Typ:", item: "Item:", power: "Stärke", accuracy: "Genauigkeit",
              pages: %w[INFO NOTIZEN WERTE ATTACKEN BÄNDER], sum_help: "Die Daten stehen oben.",
              drop: "Loslassen zum Tauschen.", grass: "GRAS", fishing: "ANGELN", cave: "HÖHLE", change: "WECHSELN", mega: "MEGA",
              eff: ["Wirkungslos", "Nicht sehr effektiv", "Sehr effektiv"], badges: "Orden:", pokedex: "Pokédex:",
              time: "Spielzeit:", money: "Geld:", started: "Beginn:", no_wild: "Hier gibt es keine wilden Pokémon.",
              busy: "", choose: "Wähle ein Pokémon." }
  }.freeze

  class << self
    attr_accessor :root, :lang
  end
  @root = nil
  @lang = "en"
  @files = {}
  @sizes = {}

  def self.l(key)
    (LABELS[@lang] || LABELS["en"])[key] || LABELS["en"][key]
  end

  # ---- the game's files ----

  # Real path of one of the game's files, whatever the letter case of its name and with or without
  # extension ("Graphics/Pictures/Party/bg" -> ".../Graphics/Pictures/Party/bg.PNG"); nil if missing.
  def self.file(rel)
    return nil if rel.nil? || rel.to_s.empty?
    rel = rel.to_s
    ckey = "#{@lang}|#{rel}"
    return @files[ckey] if @files.key?(ckey)
    @files[ckey] = localized(rel) || plain_file(rel)
  rescue StandardError
    @files[ckey] = nil
  end

  # The launcher's translated copy of an image with words drawn in it (HP -> PS, Lv -> Nv, the type names...),
  # the same one the game itself shows in the current language; nil if there is none
  def self.localized(rel)
    return nil unless defined?(AshDroidGfx) && rel =~ /\AGraphics\//i
    alt = AshDroidGfx.redirect(rel)
    alt && File.file?(alt) ? File.expand_path(alt) : nil
  rescue StandardError, NameError
    nil
  end

  def self.plain_file(rel)
    return @files[rel] if @files.key?(rel)
    base = @root || Dir.pwd
    path = File.absolute_path?(rel) ? rel : File.join(base, rel)
    found = File.file?(path) ? path : nil
    unless found
      dir = File.dirname(path)
      name = File.basename(path).downcase
      real_dir = File.directory?(dir) ? dir : real_directory(dir)
      if real_dir
        entries = Dir.children(real_dir)
        pick = entries.find { |e| e.downcase == name } ||
               entries.find { |e| File.basename(e, ".*").downcase == name && e =~ /\.(png|gif|jpe?g|bmp|ttf)\z/i }
        found = File.join(real_dir, pick) if pick
      end
    end
    @files[rel] = found
  rescue StandardError
    @files[rel] = nil
  end

  def self.real_directory(dir)
    parent = File.dirname(dir)
    return nil if parent == dir
    parent = real_directory(parent) unless File.directory?(parent)
    return nil unless parent
    pick = Dir.children(parent).find { |e| e.downcase == File.basename(dir).downcase }
    pick ? File.join(parent, pick) : nil
  rescue StandardError
    nil
  end

  def self.pic(name)
    file("Graphics/Pictures/#{name}")
  end

  # Width and height of a PNG, from its header
  def self.size(path)
    return [0, 0] unless path
    @sizes[path] ||= begin
      head = File.binread(path, 24)
      head && head.bytesize >= 24 && head[12, 4] == "IHDR" ? head[16, 8].unpack("NN") : [0, 0]
    end
  rescue StandardError
    [0, 0]
  end

  # ---- drawing operations ----

  def self.hex(c)
    c.is_a?(String) ? c : format("#%02x%02x%02x", *c.first(3).map { |v| [[v.to_i, 0].max, 255].min })
  end

  def self.shade(c, f)
    c.map { |v| [[(v * f).to_i, 0].max, 255].min }
  end

  def self.fill(ops, x, y, w, h, c)
    ops << { "o" => "fill", "r" => [x, y, w, h], "c" => hex(c) }
  end

  # Part of an image (source rectangle) at a screen position, each pixel k x k (or stretched to dw x dh)
  def self.img(ops, f, sx, sy, sw, sh, dx, dy, k = 2, extra = nil)
    return unless f
    op = { "o" => "img", "f" => f, "s" => [sx, sy, sw, sh], "d" => [dx, dy, sw * k, sh * k] }
    op.merge!(extra) if extra
    ops << op
  end

  def self.whole(ops, f, dx, dy, k = 2, extra = nil)
    w, h = size(f)
    img(ops, f, 0, 0, w, h, dx, dy, k, extra) if w > 0
  end

  # Text in the game's font. a: "l" / "m" / "r"; v: "a" (y is the top of the line) / "m" (y is its middle)
  def self.txt(ops, s, x, y, z, c, k = nil, a = "l", v = "a", extra = nil)
    return if s.nil? || s.to_s.empty?
    op = { "o" => "txt", "s" => s.to_s, "x" => x.round, "y" => y.round, "z" => z, "c" => hex(c), "a" => a, "v" => v }
    op["k"] = hex(k) if k
    op.merge!(extra) if extra
    ops << op
  end

  # Text placed as the game places it (its drawing routines add 10 px at the font's base size of 26)
  def self.gtext(ops, s, x, y, z, base, shadow, align = 0, extra = nil)
    txt(ops, s, x, y + 10 * z / 26, z, base, shadow, align == 1 ? "r" : "l", "a", extra)
  end

  # One axis of an image widened by repeating single lines (never scaled): segments [source start, source
  # length, target length]
  class Axis
    attr_reader :segs

    def initialize(len)
      @segs = [[0, len, len]]
    end

    # insert n copies of the line at position pos (a position in the image as widened so far)
    def repeat(pos, n)
      out = []
      at = 0
      @segs.each do |s0, sl, dl|
        if pos >= at && pos < at + dl
          if sl == dl
            cut = pos - at
            out << [s0, cut, cut] if cut > 0
            out << [s0 + cut, 1, n]
            out << [s0 + cut, sl - cut, dl - cut]
          else
            out << [s0, sl, dl + n]
          end
        else
          out << [s0, sl, dl]
        end
        at += dl
      end
      @segs = out
      self
    end

    # keep only positions a...b
    def crop(a, b)
      out = []
      at = 0
      @segs.each do |s0, sl, dl|
        lo = [a, at].max
        hi = [b, at + dl].min
        if hi > lo
          out << (sl == dl ? [s0 + lo - at, hi - lo, hi - lo] : [s0, sl, hi - lo])
        end
        at += dl
      end
      @segs = out
      self
    end

    def length
      @segs.inject(0) { |a, s| a + s[2] }
    end
  end

  # Draws an image through two axes at (dx, dy), k screen pixels per image pixel
  def self.sheet(ops, f, xs, ys, dx, dy, k = 2, extra = nil)
    return unless f
    y = dy
    ys.segs.each do |sy, sh, dh|
      x = dx
      xs.segs.each do |sx, sw, dw|
        op = { "o" => "img", "f" => f, "s" => [sx, sy, sw, sh], "d" => [x, y, dw * k, dh * k] }
        op.merge!(extra) if extra
        ops << op
        x += dw * k
      end
      y += dh * k
    end
  end

  # A canvas of art pixels: buttons, cards and lettering drawn by code
  class Art
    attr_reader :items, :down
    attr_accessor :bounce

    def initialize(x, y, w, h, scale = S)
      @x = x
      @y = y
      @w = w
      @h = h
      @scale = scale
      @items = []
      @down = nil
      @bounce = false
    end

    def rr(box, r, inset, col, rows = nil, into = @items)
      it = ["rr", box[0], box[1], box[2], box[3], r, inset, AshDroidDSUI.hex(col)]
      it.push(rows[0], rows[1]) if rows
      into << it
      self
    end

    def rc(box, col, into = @items)
      into << ["rc", box[0], box[1], box[2], box[3], AshDroidDSUI.hex(col)]
      self
    end

    def el(box, col, upper = false, into = @items)
      into << ["el", box[0], box[1], box[2], box[3], AshDroidDSUI.hex(col), upper ? 1 : 0]
      self
    end

    def pattern(x, y, rows, col = [255, 255, 255], edge = [48, 48, 64], into = @items)
      into << ["px", x + 1, y + 1, AshDroidDSUI.hex(edge)] + rows
      into << ["px", x, y, AshDroidDSUI.hex(col)] + rows
      self
    end

    def label(text, cx, cy, k = 1, max = 0, into = @items)
      into << ["pt", text.to_s, cx, cy, k, max]
      self
    end

    def shadow(box, r, col, into = @items)
      rr([box[0] + 1, box[1] + 3, box[2] - 1, box[3] + 3], r, 0, col, nil, into)
    end

    # Dark outline, white rim, bevelled two-tone body; each layer is the outer shape shrunk evenly
    def body(box, col, r, rim = 2, into = @items)
      h = box[3] - box[1] + 1
      sh = AshDroidDSUI.method(:shade)
      rr(box, r, 0, [56, 56, 72], nil, into)
      rr(box, r, 1, [240, 240, 248], nil, into)
      rr(box, r, rim + 1, sh.call(col, 0.55), nil, into)
      rr(box, r, rim + 2, sh.call(col, 0.84), [0, h - 5], into)
      rr(box, r, rim + 2, col, [0, (h * 0.56).to_i], into)
      rr(box, r, rim + 3, sh.call(col, 1.22), [rim + 3, rim + 5], into)
    end

    # A whole button, with its pressed look (sunk one art pixel, darker, no shadow) kept for the launcher
    def button(box, col, r, shadow_col, text = nil, center = nil, rim: 2, k: 1, icon: nil)
      shadow(box, r, shadow_col) if shadow_col
      body(box, col, r, rim)
      @down ||= []
      low = [box[0], box[1] + 1, box[2], box[3] + 1]
      body(low, AshDroidDSUI.shade(col, 0.72), r, rim, @down)
      cx, cy = center || [(box[0] + box[2]) / 2, (box[1] + box[3]) / 2]
      max = box[2] - box[0] - 2 * rim - 5
      if text
        label(text, cx, cy, k, max)
        label(text, cx, cy + 1, k, max, @down)
      end
      if icon
        icon.call(self, @items, 0)
        icon.call(self, @down, 1)
      end
      self
    end

    def op(id = nil, extra = nil)
      o = { "o" => "art", "x" => @x, "y" => @y, "w" => @w, "h" => @h, "it" => @items }
      o["S"] = @scale if @scale != S
      o["dn"] = @down if @down && id
      o["id"] = id if id
      o["bn"] = 1 if @bounce
      o.merge!(extra) if extra
      o
    end
  end

  # Collects the operations of one screen and numbers its buttons
  class Screen
    attr_reader :ops

    def initialize
      @ops = []
      @id = 0
    end

    def next_id
      @id += 1
    end

    # A button in the game's own style (see AshDroidDSUI.game_box) with its label in the button lettering.
    # (px, py): where the mock-up pasted its art canvas; box: the button in it, in art pixels. bounce: it is
    # under the cursor, which shows as the game's "selected" button.
    def button(px, py, box, col, text, cmd, shadow: nil, r: 9, rim: 2, k: 1, center: nil, bounce: false, icon: nil,
               into: @ops, disabled: false)
      x0, y0, x1, y1 = box
      w = x1 - x0 + 1
      h = y1 - y0 + 1
      id = cmd && !disabled ? next_id : nil
      sx = px + x0 * S
      sy = py + y0 * S
      AshDroidDSUI.game_box(into, sx, sy, w * S, h * S, id || bounce ? col : GREY, id, bounce)
      art = Art.new(px + (x0 - 1) * S, py + (y0 - 1) * S, w + 2, h + 2)
      cx, cy = center ? [center[0] - x0 + 1, center[1] - y0 + 1] : [(1 + w) / 2, (1 + h) / 2]
      art.label(text, cx, cy, k, w - 4) if text
      icon.call(art, art.items, 0) if icon
      art.bounce = bounce
      into << art.op unless art.items.empty?
      into << { "o" => "hit", "r" => [sx, sy, w * S, h * S], "cmd" => cmd, "art" => id } if id
    end

    def result(extra = nil)
      out = { "ops" => @ops, "fonts" => [AshDroidDSUI.file("Fonts/power green.ttf"), AshDroidDSUI.file("Fonts/power green small.ttf")] }
      out.merge!(extra) if extra
      out
    end
  end

  def self.house_icon(x, y)
    proc { |art, into, off| art.pattern(x, y + off, HOUSE, [255, 255, 255], [48, 48, 64], into) }
  end

  # Arrowhead of 5 columns: column k (from the tip) spans rows 4-k..4+k
  def self.arrow_icon(x, y, left)
    rows = (0...9).map do |r|
      (0...5).map { |c| (r - 4).abs <= (left ? c : 4 - c) ? "#" : "." }.join
    end
    proc { |art, into, off| art.pattern(x, y + off, rows, [255, 255, 255], [48, 48, 64], into) }
  end

  def self.stripes(ops)
    ops << { "o" => "stripes", "r" => [0, 0, W, H], "c1" => "#cecee8", "c2" => "#c8c8e4", "n" => 12 }
  end

  def self.upper(s)
    s.to_s.upcase.gsub("POKÉMON", "POKéMON")
  end

  # ---- battle: commands ----

  # The colours of the game's own command buttons (plain, under the cursor), used when they cannot be read
  # from the game's image at run time
  COMMAND_COLORS = [[[232, 56, 56], [248, 168, 176]], [[224, 152, 40], [248, 208, 120]],
                    [[88, 176, 40], [144, 224, 120]], [[40, 144, 200], [120, 208, 248]]].freeze
  COMMAND_ROWS = [0, 2, 1, 3].freeze            # fight, bag, Pokémon, run in the game's command button sheet

  # A battle button in the game's own shape: one of the rounded buttons of its command menu (row of its
  # sheet), without the word drawn in it (only the two ends of the image are used, and a clean column between
  # them), made as wide and as tall as needed by repeating flat lines, k screen px per image pixel. With an
  # id, the game's lighter version of the same button (the one it shows under its cursor) is the pressed
  # look. The label goes on top in the button lettering.
  def self.pill(s, x, y, w, h, row, k, text, cmd, lettering: 1, selected: false)
    f = pic("Battle/cursor_command")
    fw, fh = size(f)
    cell = fw / 2
    bh = 46
    id = cmd ? s.next_id : nil
    if cell >= 60 && fh >= (row + 1) * bh
      lw = w / k
      lh = h / k
      [0, 1].each do |variant|
        next if variant == 1 && !id
        x0 = variant * cell
        y0 = row * bh
        xs = Axis.new(fw)
        xs.instance_variable_set(:@segs, [[x0, 14, 14], [x0 + 14, 1, [lw - 28, 1].max], [x0 + cell - 14, 14, 14]])
        ys = Axis.new(fh)
        extra_rows = [lh - bh, 0].max
        ys.instance_variable_set(:@segs, [[y0, 16, 16], [y0 + 16, 1, extra_rows / 2 + 1], [y0 + 17, 13, 13],
                                          [y0 + 30, 1, extra_rows - extra_rows / 2 + 1], [y0 + 31, 15, 15]])
        extra = {}
        extra[variant == 1 ? "dn_of" : "up_of"] = id if id
        extra["bn"] = 1 if selected && variant == 0
        sheet(s.ops, f, xs, ys, x, y, k, extra)
      end
    else
      game_box(s.ops, x, y, w, h, COMMAND_COLORS[COMMAND_ROWS.index(row) || 3][0], id, selected)
    end
    aw = w / S
    ah = h / S
    art = Art.new(x + (w - aw * S) / 2, y + (h - ah * S) / 2, aw, ah)
    art.label(text, aw / 2, ah / 2, lettering, aw - 8)
    art.bounce = selected
    s.ops << art.op
    s.ops << { "o" => "hit", "r" => [x, y, w, h], "cmd" => cmd, "art" => id } if id
  end

  # d: { texts: [fight, bag, pokemon, run], index:, own: [:ok/:st/:ko/nil x6], foe: [...],
  #      rows: the row of the game's button sheet each of the four uses (it changes in the Safari Zone...) }
  def self.command(d)
    s = Screen.new
    stripes(s.ops)
    dots = Art.new(0, 0, 206, 16)
    dot = lambda do |x, st|
      col = { ok: [96, 208, 96], st: [240, 200, 64], ko: [128, 128, 144] }[st]
      dots.el([x, 4, x + 8, 12], [56, 56, 72]).el([x + 1, 5, x + 7, 11], col || [176, 176, 204])
      dots.rc([x + 3, 6, x + 4, 7], [255, 255, 255]) if col
    end
    (d[:own] || []).first(6).each_with_index { |st, i| dot.call(5 + i * 11, st) }
    (d[:foe] || []).first(6).each_with_index { |st, i| dot.call(206 - 14 - i * 11, st) }
    s.ops << dots.op
    t = (d[:texts] || []).map { |x| upper(x) }
    idx = d[:index].to_i
    row = ->(i) { ((d[:rows] || [])[i]) || COMMAND_ROWS[i] }
    # the game's own rounded command buttons; the cursor is the bob alone, never a change of colour
    pill(s, 10 * S, 19 * S, 186 * S, 94 * S, row.call(0), 6, t[0], "sel:0", lettering: 3, selected: idx == 0)
    pill(s, 4 * S, 122 * S, 67 * S, 53 * S, row.call(1), 3, t[1], "sel:1", selected: idx == 1)
    pill(s, 77 * S, 134 * S, 52 * S, 41 * S, row.call(3), 3, t[3], "sel:3", selected: idx == 3)
    pill(s, 135 * S, 122 * S, 67 * S, 53 * S, row.call(2), 3, t[2], "sel:2", selected: idx == 2)
    s.result("id" => "command")
  end

  # ---- battle: moves ----

  MOVE_CELLS = [[4, 5, 101, 71], [105, 5, 202, 71], [4, 75, 101, 141], [105, 75, 202, 141]].freeze

  def self.type_color(type)
    TYPE_COLORS[type.to_s.upcase] || [104, 160, 144]
  end

  def self.card(art, box, col, r = 8)
    art.rr(box, r, 0, [56, 56, 72]).rr(box, r, 1, [240, 240, 248])
    art.rr(box, r, 3, shade(col, 0.6)).rr(box, r, 4, col).rr(box, r, 6, [248, 248, 236])
  end

  # The game's own move button for a type (row of its sheet: blank, with the type's colour at both ends),
  # made as wide and as tall as needed by repeating its flat lines, k screen px per image pixel. With an id,
  # the game's lighter version (the one under its cursor) is the pressed look. Returns false if the game's
  # image is not there.
  def self.move_box(ops, x, y, w, h, row, k, id = nil, selected = false, extra = nil)
    f = pic("Battle/cursor_fight")
    fw, fh = size(f)
    cell = fw / 2
    bh = 46
    return false unless cell >= 60 && fh >= (row + 1) * bh
    lw = w / k
    lh = h / k
    grow = [lh - bh, 0].max
    [0, 1].each do |variant|
      next if variant == 1 && !id
      x0 = variant * cell
      y0 = row * bh
      xs = Axis.new(fw)
      xs.instance_variable_set(:@segs, [[x0, cell / 2, cell / 2], [x0 + cell / 2, 1, [lw - cell, 0].max + 1], [x0 + cell / 2 + 1, cell - cell / 2 - 1, cell - cell / 2 - 1]])
      ys = Axis.new(fh)
      ys.instance_variable_set(:@segs, [[y0, 16, 16], [y0 + 16, 1, grow / 2 + 1], [y0 + 17, 13, 13],
                                        [y0 + 30, 1, grow - grow / 2 + 1], [y0 + 31, 15, 15]])
      e = (extra || {}).dup
      e[variant == 1 ? "dn_of" : "up_of"] = id if id
      e["bn"] = 1 if selected && variant == 0
      sheet(ops, f, xs, ys, x, y, k, e)
    end
    true
  end

  EFF_COLORS = [[[120, 120, 136], [208, 208, 216]], [[200, 104, 32], [240, 208, 168]], [[40, 144, 64], [176, 224, 184]]].freeze

  # d: { moves: [{name:, type:, row:, pp:, tpp:, eff: 0/1/2/nil}], index:, mega: 0/1/2, cancel: }
  def self.fight(d)
    s = Screen.new
    stripes(s.ops)
    types = pic("types")
    idx = d[:index].to_i
    (d[:moves] || []).first(4).each_with_index do |m, i|
      x0, y0, x1, y1 = MOVE_CELLS[i]
      w = (x1 - x0 + 1) * S
      h = (y1 - y0 + 1) * S
      x = x0 * S
      y = y0 * S
      sel = i == idx
      bn = sel ? { "bn" => 1 } : {}
      id = s.next_id
      unless move_box(s.ops, x, y, w, h, m[:row].to_i, 3, id, sel)      # the game's own button for that type
        game_box(s.ops, x, y, w, h, m[:col] || type_color(m[:type]), id, sel)
      end
      cx = x + w / 2
      eff = m[:eff]
      ny = 88                                   # the same places on every button, with or without the
      iy = 156                                  # effectiveness line, so the four always line up
      txt(s.ops, m[:name], cx, y + ny, 78, INK, INK_SH, "m", "m", bn.merge("fit" => w - 96))
      img(s.ops, types, 0, m[:row].to_i * 28, 64, 28, cx - 201, y + iy, 3, bn) if types
      txt(s.ops, "PP #{m[:pp]}/#{m[:tpp]}", cx + 197, y + iy + 42, 52, INK, INK_SH, "r", "m", bn)
      txt(s.ops, l(:eff)[eff], cx, y + 300, 52, EFF_COLORS[eff][0], EFF_COLORS[eff][1], "m", "m", bn) if eff
      s.ops << { "o" => "hit", "r" => [x, y, w, h], "cmd" => "sel:#{i}", "art" => id }
    end
    pill(s, 56 * S, 147 * S, 94 * S, 29 * S, 3, 3, upper(d[:cancel] || l(:back)), "back")
    pill(s, 4 * S, 147 * S, 49 * S, 29 * S, d[:mega].to_i == 2 ? 0 : 2, 3, l(:mega), "act") if d[:mega].to_i > 0
    s.result("id" => "fight")
  end

  # ---- party ----

  SIDE = 54
  PANEL_W = 296
  U = 3

  def self.party_backdrop(ops)
    f = pic("Party/bg")
    xs = Axis.new(512).repeat(511, SIDE).repeat(0, SIDE)
    ys = Axis.new(384)
    ys.instance_variable_set(:@segs, [[320, 1, 410], [320, 50, 50], [370, 1, 66], [370, 14, 14]])
    sheet(ops, f, xs, ys, 0, 0, 2)
  end

  # The game's party panel made taller and wider by repeating lines: 256x98 -> (256 + wide) x 138
  def self.panel(ops, name, lx, ly, wide = 40, extra = nil)
    f = pic("Party/#{name}") || pic("Party/panel_rect")
    xs = Axis.new(256).repeat(128, wide)
    ys = Axis.new(98).repeat(60, 20).repeat(46, 20)
    sheet(ops, f, xs, ys, lx * 2, ly * 2, 2, extra)
  end

  def self.slot_xy(i)
    [8 + (i % 2) * 308, 6 + 16 * (i % 2) + 142 * (i / 2)]
  end

  def self.panel_name(i, mon, sel)
    return "panel_blank" unless mon
    "panel_#{i == 0 ? 'round' : 'rect'}#{mon[:hp].to_i == 0 && !mon[:egg] ? '_faint' : ''}#{sel ? '_sel' : ''}"
  end

  # What a party panel shows, at 3 screen px per pixel of the game's panel. (x, y): the panel on screen.
  # mon: { name:, icon:, level:, hp:, total:, gender: 0/1/nil, status: row or nil, item: icon file or nil, egg: }
  def self.panel_contents(ops, i, mon, x, y, sel, room = 0, extra = nil)
    at = ->(cx, cy) { [x + cx * U, y + cy * U] }
    faint = mon[:hp].to_i == 0 && !mon[:egg]
    unless mon[:egg]
      whole(ops, pic("Party/overlay_hp_back#{faint ? '_faint' : ''}"), *at.call(52 + room, 36), U, extra)
    end
    whole(ops, pic("Party/icon_ball#{sel ? '_sel' : ''}"), *at.call(1, 0), U, extra)
    img(ops, mon[:icon], 0, 0, 64, 64, *at.call(10, 2), U, extra) if mon[:icon]
    gtext(ops, mon[:name], *at.call(80, 1), 78, WHITE, DARK, 0, extra)
    if mon[:gender] == 0
      gtext(ops, "♂", *at.call(190 + room, 1), 78, [0, 112, 248], [120, 184, 232], 1, extra)
    elsif mon[:gender] == 1
      gtext(ops, "♀", *at.call(190 + room, 1), 78, [232, 32, 16], [248, 168, 184], 1, extra)
    end
    unless mon[:egg]
      hp = mon[:hp].to_i
      total = [mon[:total].to_i, 1].max
      if hp > 0
        w = [1, hp * 96.0 / total].max
        w = (w / 2).round * 2
        zone = hp <= total / 4 ? 2 : hp <= total / 2 ? 1 : 0
        img(ops, pic("Party/overlay_hp"), 0, zone * 8, w, 8, *at.call(84 + room, 38), U, extra)
      end
      row = faint ? 5 : mon[:status]
      img(ops, pic("statuses"), 0, 16 * row, 44, 16, *at.call(mon[:item] ? 82 : 50, 66), U, extra) if row
      gtext(ops, "#{hp}/#{total}", *at.call(190 + room, 50), 78, WHITE, DARK, 1, extra)
      whole(ops, pic("Party/overlay_lv"), *at.call(6, 68), U, extra)
      gtext(ops, mon[:level].to_s, *at.call(28, 55), 78, WHITE, DARK, 0, (extra || {}).merge("f" => 1))
    end
    whole(ops, mon[:item], *at.call(46, 46), 2, extra) if mon[:item]
  end

  # Help line in a window drawn with the game's menu frame, the home button (on screens more than one step
  # away from home) and the back button
  def self.footer(s, line, home, back_cmd = "back")
    if line && !line.to_s.empty?
      skin = file("Graphics/Windowskins/choice 1")
      bw = home ? 300 : 372
      bh = 64
      sheet(s.ops, skin, Axis.new(48).repeat(24, bw - 48), Axis.new(48).repeat(24, bh - 48), 12, H - bh * 2 - 12, 2)
      txt(s.ops, line, 48, H - 12 - bh - 52 + 20, 52, [80, 80, 88], [160, 160, 168], "l", "a", { "fit" => bw * 2 - 64 })
    end
    px = W - 100 * S - 8
    py = H - 26 * S - 2
    s.button(px, py, [29, 1, 98, 22], BLUE, l(:back), back_cmd, shadow: FOOT_SHADOW, center: [63, 11])
    s.button(px, py, [1, 1, 24, 22], GREEN, nil, "home", shadow: FOOT_SHADOW, icon: house_icon(7, 6)) if home
  end

  # d: { mons: [mon or nil x6], sel:, help:, drag: true/false, home: }
  def self.party(d)
    s = Screen.new
    party_backdrop(s.ops)
    mons = (0...6).map { |i| (d[:mons] || [])[i] }
    mons.each_with_index do |mon, i|
      lx, ly = slot_xy(i)
      panel(s.ops, "panel_blank", lx, ly)
      next unless mon
      g = { "g" => i }
      sel = i == d[:sel].to_i
      panel(s.ops, panel_name(i, mon, sel), lx, ly, 40, g)
      panel_contents(s.ops, i, mon, lx * 2, ly * 2, sel, 0, g)
      next if d[:message]                       # a message is up: a touch anywhere confirms it
      hit = { "o" => "hit", "r" => [lx * 2, ly * 2, PANEL_W * 2, 276], "cmd" => "sel:#{i}", "dim" => 1, "n" => i, "g" => i }
      hit["drag"] = "swap" if d[:drag]
      s.ops << hit
    end
    footer(s, d[:help], d[:home])
    s.result("id" => "party", "tap" => d[:message] ? "a" : nil)
  end

  CARD_W = 356

  def self.party_card(s, mon, i)
    room = (CARD_W - PANEL_W) * 2 / U
    x0 = (620 - CARD_W) / 2
    panel(s.ops, panel_name(i, mon, true), x0, 8, CARD_W - 256)
    panel_contents(s.ops, i, mon, x0 * 2, 16, true, room)
  end

  def self.option_color(o)
    { use: GREEN, give: ORANGE, take: RED, toss: RED, summary: BLUE, item: ORANGE, switch: GREEN, cancel: BLUE }[o[:kind]] || TEAL
  end

  # After choosing a Pokémon: its panel and what the game offers for it, stacked.
  # d: { mon:, slot:, options: [{label:, cmd:, kind:}], index:, help: }
  def self.party_actions(d)
    s = Screen.new
    party_backdrop(s.ops)
    party_card(s, d[:mon], d[:slot].to_i)
    opts = d[:options] || []
    bw = CARD_W * 2 / S
    gx = (W - (bw + 2) * S) / 2
    cols = opts.length > 4 ? 2 : 1
    rows = (opts.length / cols.to_f).ceil
    high, pitch = rows > 3 ? [22, 25] : [27, 31]
    cw = cols == 2 ? (bw - 4) / 2 : bw
    opts.each_with_index do |o, n|
      c = n % cols
      by = (n / cols) * pitch
      x = 1 + c * (cw + 4)
      s.button(gx, 314, [x, by + 1, x + cw - 1, by + high], option_color(o), upper(o[:label]), o[:cmd], shadow: PARTY_SHADOW,
               center: [x + cw / 2, by + (high + 1) / 2], bounce: n == d[:index].to_i)
    end
    footer(s, d[:help], true)
    s.result("id" => "actions")
  end

  # In battle: the Pokémon's panel, its types and its moves, and CAMBIAR.
  # d: { mon:, slot:, types: [rows], moves: [{name:, row:, pp:, tpp:}], can_switch:, switch_cmd: }
  def self.battle_info(d)
    s = Screen.new
    party_backdrop(s.ops)
    party_card(s, d[:mon], d[:slot].to_i)
    pw = 146
    ph = 96
    panel = Art.new((W - pw * S) / 2, 300, pw, ph)
    box = [0, 0, pw - 1, ph - 1]
    panel.rr(box, 8, 0, [56, 56, 72]).rr(box, 8, 1, [240, 240, 248]).rr(box, 8, 3, [248, 248, 236])
    s.ops << panel.op
    types = pic("types")
    left = 224
    right = 1016
    icon = ->(row, x, cy) { img(s.ops, types, 0, row.to_i * 28, 64, 28, x, cy - 42, 3) }
    txt(s.ops, l(:type), left, 372, 65, INK, INK_SH, "l", "m")
    (d[:types] || []).first(2).each_with_index { |row, n| icon.call(row, left + 210 + 204 * n, 372) }
    fill(s.ops, left, 432, right - left + 1, 6, [208, 208, 196])
    4.times do |n|
      y = 498 + 100 * n
      m = (d[:moves] || [])[n]
      if m
        icon.call(m[:row], left, y)
        txt(s.ops, m[:name], left + 210, y, 65, INK, INK_SH, "l", "m", { "fit" => 420 })
        txt(s.ops, "#{m[:pp]}/#{m[:tpp]}", right, y, 65, INK, INK_SH, "r", "m")
      else
        txt(s.ops, "-", left + 210, y, 65, INK, INK_SH, "l", "m")
      end
    end
    py = H - 26 * S - 2
    can = d[:can_switch]
    s.button(2, py, [2, 1, 100, 22], can ? GREEN : GREY, l(:change), can ? d[:switch_cmd] : nil, shadow: FOOT_SHADOW,
             center: [51, 11], bounce: can, disabled: !can)
    s.button(2, py, [106, 1, 129, 22], GREEN, nil, "home", shadow: FOOT_SHADOW, icon: house_icon(7, 6))
    s.button(2, py, [134, 1, 203, 22], BLUE, l(:back), "back", shadow: FOOT_SHADOW, center: [168, 11])
    s.result("id" => "battle_info")
  end

  # The party screen is on the game screen; this shows everything about the Pokémon under its cursor and what
  # can be done with it, so nothing has to be opened to look.
  # d: { mon:, slot:, first:, last:, types: [rows], item_name:, moves: [{name:, row:, pp:, tpp:}],
  #      options: [{label:, cmd:, kind:}], help: }
  def self.party_sheet(d)
    s = Screen.new
    party_backdrop(s.ops)
    mon = d[:mon]
    # whose sheet this is: icon and name, with previous / next at the sides
    kids = []
    kids << { "o" => "img", "f" => mon[:icon], "s" => [0, 0, 64, 64], "d" => [0, -6, 192, 192] } if mon[:icon]
    kids << { "o" => "txt", "s" => mon[:name].to_s, "x" => 0, "y" => 60, "z" => 78, "c" => hex(INK), "k" => hex([176, 200, 200]), "a" => "l", "v" => "a" }
    s.ops << { "o" => "hb", "cx" => W / 2, "ops" => kids }
    game_button(s, 6, 12, 112, 76, nil, d[:first] ? nil : "mon:prev", nil, glyph: arrow_icon(16, 8, true))
    game_button(s, 620 - 118, 12, 112, 76, nil, d[:last] ? nil : "mon:next", nil, glyph: arrow_icon(16, 8, false))
    pw = 202
    ph = 97
    px = (W - pw * S) / 2
    py = 196
    art = Art.new(px, py, pw, ph)
    box = [0, 0, pw - 1, ph - 1]
    art.rr(box, 8, 0, [56, 56, 72]).rr(box, 8, 1, [240, 240, 248]).rr(box, 8, 3, [248, 248, 236])
    s.ops << art.op
    types = pic("types")
    left = px + 42
    right = px + pw * S - 42
    icon = ->(row, x, cy) { img(s.ops, types, 0, row.to_i * 28, 64, 28, x, cy - 42, 3) }
    txt(s.ops, l(:type), left, py + 66, 65, INK, INK_SH, "l", "m")
    (d[:types] || []).first(2).each_with_index { |row, n| icon.call(row, left + 250 + 204 * n, py + 66) }
    txt(s.ops, l(:item), left, py + 152, 65, INK, INK_SH, "l", "m")
    if mon[:item]
      whole(s.ops, mon[:item], left + 244, py + 152 - 48, 2)
      txt(s.ops, d[:item_name], left + 244 + 104, py + 152, 65, INK, INK_SH, "l", "m", { "fit" => right - left - 350 })
    else
      txt(s.ops, "-", left + 250, py + 152, 65, INK, INK_SH, "l", "m")
    end
    fill(s.ops, left, py + 202, right - left + 1, 6, [208, 208, 196])
    4.times do |n|
      y = py + 258 + 86 * n
      m = (d[:moves] || [])[n]
      if m
        icon.call(m[:row], left, y)
        txt(s.ops, m[:name], left + 210, y, 65, INK, INK_SH, "l", "m", { "fit" => right - left - 210 - 230 })
        txt(s.ops, "PP #{m[:pp]}/#{m[:tpp]}", right, y, 65, INK, INK_SH, "r", "m")
      else
        txt(s.ops, "-", left + 210, y, 65, INK, INK_SH, "l", "m")
      end
    end
    # what the game offers for this one and VOLVER: one grid of the game's own buttons, VOLVER always last
    cells = (d[:options] || []).first(5) + [{ label: l(:back), cmd: "back", kind: :cancel }]
    t = cells.length
    cols = t <= 3 ? t : t == 4 ? 2 : 3
    rows = (t / cols.to_f).ceil
    gap = 6
    bw = (620 - 12 - gap * (cols - 1)) / cols
    bh = 62
    y0 = 540 - 6 - rows * bh - (rows - 1) * gap
    cells.each_with_index do |o, n|
      n = cols * rows - 1 if n == t - 1
      game_button(s, 6 + (n % cols) * (bw + gap), y0 + (n / cols) * (bh + gap), bw, bh, o[:label], o[:cmd],
                  option_color(o))
    end
    s.result("id" => "sheet")
  end

  # The body of a button in the game's own style: the button image of its party screen, made as wide and as
  # tall as needed by repeating (or leaving out) its flat lines, and repainted in the colour of what the
  # button does. With an id, the launcher swaps it for the game's "selected" version of that image (lighter,
  # red border) while the finger is on it. selected: it is under the cursor, which keeps its colour and
  # bobs (decided: the cursor never changes a button's colour).
  # Screen px; the image is drawn at 2x.
  def self.game_box(ops, x, y, w, h, col, id = nil, selected = false)
    lw = w / 2
    lh = h / 2
    ["", "_sel"].each do |suffix|
      pressed = suffix == "_sel"
      next if pressed && !id
      f = pic("Party/icon_cancel#{suffix}")
      fw, fh = size(f)
      next if fw <= 0
      xs = Axis.new(fw)
      if lw >= fw
        xs.repeat(fw / 2, lw - fw)
      else                                       # narrower than the image: its two ends, without the middle
        a = lw / 2
        xs.instance_variable_set(:@segs, [[0, a, a], [fw - (lw - a), lw - a, lw - a]])
      end
      ys = Axis.new(fh)
      if lh >= fh
        ys.repeat(fh * 3 / 4, (lh - fh) / 2).repeat(fh / 4, lh - fh - (lh - fh) / 2)
      else
        a = lh / 2
        ys.instance_variable_set(:@segs, [[0, a, a], [fh - (lh - a), lh - a, lh - a]])
      end
      extra = {}
      extra[pressed ? "dn_of" : "up_of"] = id if id
      extra["bn"] = 1 if selected && !pressed
      tone = pressed ? col.map { |v| v + (255 - v) * 45 / 100 } : col
      extra["fx"] = "tint:#{hex(tone)}"
      sheet(ops, f, xs, ys, x, y, 2, extra)
    end
  end

  # A game-style button given in logical px (x2 on screen), with a label or a drawn glyph
  def self.game_button(s, lx, ly, lw, lh, label, cmd, col = nil, selected = false, glyph: nil)
    id = cmd ? s.next_id : nil
    col = cmd || selected ? (col || BLUE) : GREY   # grey: nothing to do with it right now
    game_box(s.ops, lx * 2, ly * 2, lw * 2, lh * 2, col, id, selected)
    aw = lw * 2 / S
    ah = lh * 2 / S
    art = Art.new(lx * 2 + (lw * 2 - aw * S) / 2, ly * 2 + (lh * 2 - ah * S) / 2, aw, ah)
    if glyph
      glyph.call(art, art.items, 0)
    else
      art.label(upper(label), aw / 2, ah / 2, 1, aw - 6)
    end
    s.ops << art.op
    s.ops << { "o" => "hit", "r" => [lx * 2, ly * 2, lw * 2, lh * 2], "cmd" => cmd, "art" => id } if id
  end

  # ---- summary ----

  # The game's summary is on the game screen; this is its remote.
  # d: { name:, icon:, page: 0..4, first:, last:, egg: }
  def self.summary(d)
    s = Screen.new
    party_backdrop(s.ops)
    kids = []
    kids << { "o" => "img", "f" => d[:icon], "s" => [0, 0, 64, 64], "d" => [0, -6, 192, 192] } if d[:icon]
    kids << { "o" => "txt", "s" => d[:name].to_s, "x" => 0, "y" => 60, "z" => 78, "c" => hex(INK), "k" => hex([176, 200, 200]), "a" => "l", "v" => "a" }
    s.ops << { "o" => "hb", "cx" => W / 2, "ops" => kids }
    uw = 145
    x0 = 13
    px = (W - (uw + 2) * S) / 2
    [true, false].each do |left|
      bx = left ? 1 : uw - 25
      usable = left ? !d[:first] : !d[:last]
      s.button(px, 8, [bx, 1, bx + 24, 25], usable ? BLUE : GREY, nil, left ? "sum:prev" : "sum:next", shadow: PARTY_SHADOW,
               icon: arrow_icon(11, 9, left), disabled: !usable)
    end
    l(:pages).each_with_index do |label, n|
      by = 30 + n * 23
      on = n == d[:page].to_i
      s.button(px, 8, [x0 + 1, by, x0 + 119, by + 20], on ? ORANGE : GREY, label, d[:egg] ? nil : "page:#{n}", shadow: PARTY_SHADOW,
               center: [x0 + 60, by + 10], disabled: d[:egg] && !on)
    end
    footer(s, l(:sum_help), true)
    s.result("id" => "summary")
  end

  SUM_CELLS = [[4, 3, 101, 74], [105, 3, 202, 74], [4, 78, 101, 149], [105, 78, 202, 149]].freeze

  # The Pokémon's moves, all at once. Dragging one onto another swaps them.
  # d: { name:, icon:, moves: [{name:, type:, row:, cat:, power:, accuracy:, pp:, tpp:}], drag: }
  def self.moves(d)
    s = Screen.new
    stripes(s.ops)
    types = pic("types")
    cats = pic("category")
    (d[:moves] || []).first(4).each_with_index do |m, i|
      x0, y0, x1, y1 = SUM_CELLS[i]
      w = (x1 - x0 + 1) * S
      h = (y1 - y0 + 1) * S
      x = x0 * S
      y = y0 * S
      g = { "g" => i }
      unless move_box(s.ops, x, y, w, h, m[:row].to_i, 3, nil, false, g)
        game_box(s.ops, x, y, w, h, type_color(m[:type]))
      end
      cx = x + w / 2
      txt(s.ops, m[:name], cx, y + 84, 78, INK, INK_SH, "m", "m", g.merge("fit" => w - 96))
      img(s.ops, types, 0, m[:row].to_i * 28, 64, 28, cx - 201, y + 132, 3, g)
      img(s.ops, cats, 0, m[:cat].to_i * 28, 64, 28, cx + 9, y + 132, 3, g)
      rows = [[l(:power), m[:power]], [l(:accuracy), m[:accuracy]], ["PP", "#{m[:pp]}/#{m[:tpp]}"]]
      rows.each_with_index do |(label, value), n|
        ry = y + 252 + 50 * n
        txt(s.ops, label, cx - 201, ry, 52, INK, INK_SH, "l", "m", g)
        txt(s.ops, value.to_s, cx + 197, ry, 52, INK, INK_SH, "r", "m", g)
      end
      hit = { "o" => "hit", "r" => [x, y, w, h], "n" => i, "g" => i }
      hit["drag"] = "mswap" if d[:drag]
      s.ops << hit
    end
    s.button(0, 0, [134, 155, 201, 177], BLUE, l(:back), "moves:close", shadow: LAVENDER_SHADOW, center: [167, 166])
    s.button(0, 0, [106, 155, 129, 177], GREEN, nil, "home", shadow: LAVENDER_SHADOW, icon: house_icon(7, 7))
    img(s.ops, d[:icon], 0, 0, 64, 64, 12, H - 198, 3) if d[:icon]
    txt(s.ops, d[:name], 12 + 192 + 6, H - 96, 78, INK, [176, 176, 200], "l", "m", { "fit" => 400 })
    s.result("id" => "moves")
  end

  # ---- bag ----

  BAG_ORANGE = [232, 128, 80].freeze
  BAG_INK = [88, 88, 80].freeze
  BAG_INK_SH = [168, 184, 184].freeze
  POCKET_KEEP = "keep:#0070f8,#90d0f8,#f8f8f8,#b8b8b8,#d8d8d8".freeze
  POCKET_COLORS = [[[232, 136, 192], [168, 80, 128], [200, 104, 160]], [[248, 120, 64], [136, 80, 32], [192, 96, 48]],
                   [[232, 184, 40], [136, 112, 32], [248, 248, 248]], [[168, 232, 72], [96, 144, 56], [248, 248, 248]],
                   [[64, 192, 64], [48, 128, 80], [56, 160, 72]], [[40, 208, 200], [48, 136, 128], [248, 248, 248]],
                   [[80, 128, 232], [16, 80, 192], [48, 104, 216]], [[152, 88, 240], [96, 24, 216], [128, 56, 232]]].freeze

  def self.pocket_face(art, into, selected, light, dark, mid, off, k)
    tone = ->(c) { c.map { |v| (v * k).to_i } }
    art.el([2, 5, 57, 60], [176, 88, 48], false, into) if off == 0
    art.el([2, 2 + off, 57, 57 + off], [72, 72, 72], false, into)
    art.el([4, 4 + off, 55, 55 + off], selected ? [248, 248, 248] : tone.call([200, 200, 200]), false, into)
    i = selected ? 8 : 6
    box = [i, i + off, 59 - i, 59 - i + off]
    art.el(box, tone.call(dark), false, into)
    art.el(box, tone.call(light), true, into)
    art.rc([i + 1, 28 + off, 58 - i, 29 + off], tone.call(mid), into)
  end

  # d: { pocket: 1.., pockets: n, colors: [[light, dark, mid] per pocket] or nil, items: [{icon:, name:, qty:}],
  #      index:, desc:, state: :world / :battle / :item, options: [{label:, cmd:, kind:}], opt_index: }
  def self.bag(d)
    s = Screen.new
    ops = s.ops
    state = d[:state] || :world
    cut = 180
    top = 92
    row = 43
    message = d[:message].to_s
    talking = !message.empty?                  # the game is saying or asking something: the foot shows it
    grow_list, grow_desc = state == :world && !talking ? [44, 24] : [1, 67]
    pocket = [d[:pocket].to_i, 1].max
    src = pic("Bag/bg_#{pocket}") || pic("Bag/bg_1")
    fill(ops, 0, 0, W, H, BAG_ORANGE)
    img(ops, src, 0, 0, 1, 4, 0, 0, 2, { "d" => [0, 0, W, 8] })
    xs = Axis.new(512).crop(cut, 512).repeat(300 - cut, 620 - 332)
    ys = Axis.new(384).crop(4, 384).repeat(292 - 4, grow_desc).repeat(150, grow_list)
    sheet(ops, src, xs, ys, 0, top * 2, 2)
    fill(ops, 0, top * 2, (186 - cut) * 2, (270 + grow_list + 3) * 2, BAG_ORANGE)
    sheet_f = pic("Bag/icon_pocket")
    count = [[d[:pockets].to_i, 1].max, 8].min
    count.times do |i|
      x = 62 + i * 62
      y = 16
      light, dark, mid = ((d[:colors] || [])[i]) || POCKET_COLORS[i % 8]
      selected = i + 1 == pocket
      art = Art.new(x * 2, y * 2, 60, 63, 2)
      pocket_face(art, art.items, selected, light, dark, mid, 0, 1.0)
      down = []
      pocket_face(art, down, selected, light, dark, mid, 2, 0.72)
      art.instance_variable_set(:@down, down)
      id = s.next_id
      ops << art.op(id)
      img(ops, sheet_f, i * 28, 0, 28, 28, (x + 30) * 2 - 42, (y + 30) * 2 - 42, 3, { "fx" => POCKET_KEEP }) if sheet_f
      ops << { "o" => "hit", "r" => [x * 2, y * 2, 120, 124], "cmd" => "pocket:#{i + 1}", "art" => id } unless talking
    end
    # the list
    lx = 6
    ly = top + 10
    lw = 620 - 6 - 52
    visible = state == :world && !talking ? 7 : 6
    items = d[:items] || []
    sel = d[:index].to_i
    inner = []
    cur = pic("Bag/cursor")
    if cur && sel < items.length
      cw, = size(cur)
      sheet(inner, cur, Axis.new(cw).repeat(141, lw - cw + 8), Axis.new(size(cur)[1]), 0, (row * sel - 7) * 2, 2)
    end
    items.each_with_index do |it, i|
      y = row * i
      whole(inner, it[:icon], 14 * 2, (y + 2) * 2, 2) if it[:icon]
      gtext(inner, it[:name], 70 * 2, (y + 8) * 2, 52, BAG_INK, BAG_INK_SH, 0, { "fit" => (lw - 70 - 90) * 2 })
      gtext(inner, format("x%3d", it[:qty]), (lw - 22) * 2, (y + 8) * 2, 52, BAG_INK, BAG_INK_SH, 1) if it[:qty]
      inner << { "o" => "hit", "r" => [0, y * 2, (lw + 8) * 2, row * 2], "cmd" => "item:#{i}", "dim" => 1 } unless talking
    end
    view_h = row * visible * 2
    ops << { "o" => "scroll", "key" => "bag#{pocket}", "r" => [(lx - 2) * 2, (ly - 5) * 2, (lw + 8) * 2, view_h],
             "ch" => row * items.length * 2, "ops" => inner, "see" => [row * sel * 2, row * (sel + 1) * 2],
             "bar" => [(620 - 28) * 2 + 4, (ly + 24) * 2, 12, view_h - 116], "bc1" => "#00000000", "bc2" => "#58483c" }
    # the foot
    foot = top + 284 + grow_list
    desc = d[:desc].to_s
    sh = [72, 76, 84]
    opts = d[:options] || []
    if talking
      txt(ops, message, 48, (foot + 20) * 2, 52, WHITE, [0, 0, 0], "l", "a", { "ww" => W - 96, "lh" => 64, "ml" => 2 })
      py = foot * 2 + 12 + 27 * S
      opts.first(2).each_with_index do |o, n|
        x = 2 + n * 100
        s.button(14, py, [x, 1, x + 97, 24], o[:kind] == :cancel ? BLUE : GREEN, upper(o[:label]), o[:cmd], shadow: sh,
                 center: [x + 49, 13], bounce: n == d[:opt_index].to_i)
      end
      return s.result("id" => "bag", "tap" => opts.empty? ? "a" : nil)
    end
    case state
    when :battle
      txt(ops, desc, 48, (foot + 20) * 2, 52, WHITE, [0, 0, 0], "l", "a", { "ww" => W - 96, "lh" => 64, "ml" => 2 })
      py = foot * 2 + 12 + 27 * S
      can = d[:can_use]
      s.button(14, py, [2, 1, 99, 24], can ? GREEN : GREY, upper(d[:use_label]), can ? "use" : nil, shadow: sh, center: [51, 13], disabled: !can)
      s.button(14, py, [102, 1, 199, 24], BLUE, l(:back), "back", shadow: sh, center: [151, 13])
    when :item
      cols = opts.length > 4 ? 3 : 2
      bw = cols == 3 ? 64 : 98
      opts.first(6).each_with_index do |o, n|
        x = 2 + (n % cols) * (bw + (cols == 3 ? 3 : 2))
        y = 1 + (n / cols) * 27
        s.button(14, foot * 2 + 12, [x, y, x + bw - 1, y + 23], o[:kind] == :give ? PURPLE : option_color(o), upper(o[:label]), o[:cmd], shadow: sh,
                 center: [x + bw / 2, y + 12], bounce: n == d[:opt_index].to_i)
      end
    else
      txt(ops, desc, 48, (foot + 20) * 2, 52, WHITE, [0, 0, 0], "l", "a", { "ww" => 820, "lh" => 64, "ml" => 3 })
      kw = 50
      s.button(W - kw * S - 56, foot * 2 + 34, [1, 1, 48, 29], BLUE, l(:back), "back", shadow: sh, center: [25, 15])
    end
    s.result("id" => "bag")
  end

  # ---- overworld home ----

  def self.topbar(ops, left, right_parts)
    fill(ops, 0, 0, W, 15 * S, [72, 72, 104])
    fill(ops, 0, 15 * S, W, S, [240, 240, 248])
    txt(ops, left, 6 * S, 7 * S, 52, [255, 255, 255], [40, 40, 64], "l", "m", { "fit" => 700 })
    kids = right_parts.each_with_index.map do |part, i|
      { "o" => "txt", "s" => part.to_s, "x" => 0, "y" => 7 * S, "z" => 52, "c" => "#ffffff", "k" => "#282840", "a" => "l", "v" => "m",
        "gap" => i == 0 ? 0 : 8 }
    end
    ops << { "o" => "hb", "cx" => (206 - 6) * S, "ax" => "r", "ops" => kids }
  end

  # d: { place:, time: "12:34", mons: [{name:, icon:, level:, hp:, total:, status:, item:, egg:}], trainer:, free:,
  #      save_label:, bag_label:, cursor: nil or 0..4 }
  def self.home(d)
    s = Screen.new
    ops = s.ops
    stripes(ops)
    hh, mm = d[:time].to_s.split(":")
    topbar(ops, upper(d[:place]), [hh.to_s, ":", mm.to_s])
    pw = 340
    ph = 80
    pitch = 81
    xs = Axis.new(256).repeat(128, pw - 256)
    compact = Axis.new(98)
    compact.instance_variable_set(:@segs, [[0, 30, 30], [39, 31, 31], [79, 19, 19]])
    mons = d[:mons] || []
    6.times do |i|
      y = 102 + i * pitch * 2
      mon = mons[i]
      unless mon
        sheet(ops, pic("Party/panel_blank"), xs, compact, 10, y, 2)
        next
      end
      faint = mon[:hp].to_i == 0 && !mon[:egg]
      sheet(ops, pic("Party/panel_rect#{faint ? '_faint' : ''}"), xs, compact, 10, y, 2)
      whole(ops, pic("Party/icon_ball"), 10 + 16, y + 4, 2)
      gtext(ops, mon[:name], 10 + 212, y + 12, 52, WHITE, DARK, 0, { "fit" => 300 })
      # the game's icons are 32x32 drawings stored doubled: 5 screen px per drawn pixel is still pixel-perfect
      if mon[:icon]
        ops << { "o" => "img", "f" => mon[:icon], "s" => [0, 0, 64, 64], "d" => [10 + 124 - 80, y + 72 - 80, 160, 160] }
      end
      if mon[:item]
        iw, ih = size(mon[:item])
        ops << { "o" => "img", "f" => mon[:item], "s" => [0, 0, iw, ih], "d" => [10 + 148, y + 82, iw * 3 / 2, ih * 3 / 2] }
      end
      unless mon[:egg]
        whole(ops, pic("Party/overlay_lv"), 10 + 24, y + 120, 2)
        gtext(ops, mon[:level].to_s, 10 + 68, y + 94, 52, WHITE, DARK, 0, { "f" => 1 })
        whole(ops, pic("Party/overlay_hp_back#{faint ? '_faint' : ''}"), 10 + 212, y + 90, 3)
        hp = mon[:hp].to_i
        total = [mon[:total].to_i, 1].max
        if hp > 0
          w = [(hp * 96.0 / total / 2).round * 2, 2].max
          zone = hp <= total / 4 ? 2 : hp <= total / 2 ? 1 : 0
          img(ops, pic("Party/overlay_hp"), 0, zone * 8, w, 8, 10 + 212 + 96, y + 90 + 6, 3)
        end
        row = faint ? 5 : mon[:status]
        img(ops, pic("statuses"), 0, 16 * row, 44, 16, 10 + pw * 2 - 132 - 28, y + 24, 3) if row
      end
    end
    free = d[:free]
    labels = [["POKéMON", PINK, "open:party"], [upper(d[:bag_label]), ORANGE, "open:bag"], [upper(d[:map_label]), GREEN, "open:map"],
              [upper(d[:trainer]), TEAL, "open:card"], [upper(d[:save_label]), BLUE, "open:save"]]
    labels.each_with_index do |(label, col, cmd), i|
      on = free && !(d[:off] || []).include?(i)
      s.button(0, 0, [118, 20 + i * 31, 201, 20 + i * 31 + 27], on ? col : GREY, label, on ? cmd : nil, shadow: LAVENDER_SHADOW,
               bounce: on && d[:cursor] == i, disabled: !on)
    end
    s.result("id" => "home", "tap" => free ? nil : "a")
  end

  # ---- region map ----

  # d: { region:, place:, map: file, marker: file or nil, pos: [x, y] in squares, zoom: 2..4, wild: true/false }
  def self.region_map(d)
    s = Screen.new
    ops = s.ops
    side = 54
    bottom = 156
    f = pic("mapbg")
    sheet(ops, f, Axis.new(512).repeat(505, side).repeat(5, side), Axis.new(384).repeat(378, bottom), 0, 0, 2) if f
    fill(ops, 0, 0, W, 84, [24, 40, 136])
    fill(ops, 0, 0, W, 8, [72, 96, 240])
    fill(ops, 0, 8, W, 8, [48, 72, 208])
    title = [d[:region], d[:place]].compact.reject { |x| x.to_s.empty? }.join("  ·  ")
    gtext(ops, title, 36, 2, 52, WHITE, [0, 0, 0], 0, { "fit" => 800 })
    gtext(ops, l(:here), W - 32, 2, 52, WHITE, [0, 0, 0], 1)
    zoom = [[d[:zoom].to_i, 2].max, 4].min
    top = 84
    rowh = 196
    vw = W
    vh = H - rowh - top
    mw, mh = size(d[:map])
    px = -8 + d[:pos][0].to_i * 16
    py = -8 + d[:pos][1].to_i * 16
    # the map can be moved with the finger; it starts centred on the player. Smaller than the view (the whole
    # region fits): it sits centred inside the game's frame.
    cw = mw * zoom
    ch = mh * zoom
    pw = [cw, vw - 8].min
    phh = [ch, vh - 8].min
    ox = (vw - 8 - pw) / 2
    oy = (vh - 8 - phh) / 2
    whole_map = pw != vw - 8 || phh != vh - 8
    fill(ops, 0, top, vw, vh, whole_map ? [24, 40, 136] : [0, 0, 0])
    fill(ops, ox, top + oy, pw + 8, phh + 8, [0, 0, 0]) if whole_map
    view = { "o" => "pan", "key" => "map#{zoom}", "r" => [4 + ox, top + 4 + oy, pw, phh], "cw" => cw, "ch" => ch,
             "x0" => [[(px + 16) * zoom - pw / 2, cw - pw].min, 0].max, "y0" => [[(py + 16) * zoom - phh / 2, ch - phh].min, 0].max, "ops" => [] }
    inner = view["ops"]
    whole(inner, d[:map], 0, 0, zoom) if mw > 0
    whole(inner, d[:marker], px * zoom, py * zoom, zoom) if d[:marker]
    cursor = pic("mapCursor")
    img(inner, cursor, 0, 0, 32, 32, px * zoom, py * zoom, zoom) if cursor
    ops << view
    sh = [16, 24, 96]
    zx = W - 24 * S - 6
    zy = top + vh - 50 * S - 6
    bar = lambda do |art, into, off, plus|
      art.rc([7, 11 + off, 17, 13 + off], [48, 48, 64], into)
      art.rc([11, 7 + off, 13, 17 + off], [48, 48, 64], into) if plus
      art.rc([6, 10 + off, 16, 12 + off], [255, 255, 255], into)
      art.rc([10, 6 + off, 12, 16 + off], [255, 255, 255], into) if plus
    end
    plus = proc { |art, into, off| bar.call(art, into, off, true) }
    minus = proc { |art, into, off| bar.call(art, into, off, false) }
    s.button(zx, zy, [1, 1, 22, 22], zoom == 4 ? [136, 136, 160] : BLUE, nil, zoom == 4 ? nil : "zoom:#{zoom + 1}", shadow: sh, icon: plus, disabled: zoom == 4)
    s.button(zx, zy, [1, 26, 22, 47], zoom == 2 ? [136, 136, 160] : BLUE, nil, zoom == 2 ? nil : "zoom:#{zoom - 1}", shadow: sh, icon: minus, disabled: zoom == 2)
    fill(ops, 0, H - rowh, W, rowh, [32, 48, 152])
    by = H - rowh + 2
    wild = d[:wild]
    s.button(2, by, [2, 2, 128, 28], wild ? GREEN : [136, 136, 160], l(:wild), wild ? "wild" : nil, shadow: sh, center: [65, 15], disabled: !wild)
    s.button(2, by, [134, 2, 203, 28], BLUE, l(:back), "back", shadow: sh, center: [168, 15])
    s.result("id" => "map")
  end

  # ---- wild Pokémon of the place ----

  # d: { place:, tabs: [{label:, col:}], tab:, rows: [{icon:, name:, seen:, lo:, hi:, pct:}] }
  def self.route(d)
    s = Screen.new
    ops = s.ops
    stripes(ops)
    fill(ops, 0, 0, W, 15 * S, [72, 72, 104])
    fill(ops, 0, 15 * S, W, S, [240, 240, 248])
    txt(ops, upper(d[:place]), 6 * S, 7 * S, 52, [255, 255, 255], [40, 40, 64], "l", "m", { "fit" => 620 })
    txt(ops, l(:wild_title), (206 - 6) * S, 7 * S, 52, [255, 255, 255], [40, 40, 64], "r", "m")
    tabs = d[:tabs] || []
    n = [tabs.length, 1].max
    tw = (200 - 3 * (n - 1)) / n
    tabs.each_with_index do |t, i|
      x = 4 + i * (tw + 3)
      on = i == d[:tab].to_i
      s.button(0, 0, [x, 17, x + tw - 1, 38], on ? (t[:col] || GREEN) : GREY, upper(t[:label]), "tab:#{i}", shadow: LAVENDER_SHADOW, rim: 1,
               center: [x + tw / 2, 27])
    end
    rows = d[:rows] || []
    inner = []
    rows.each_with_index do |r, i|
      y = i * 23 * S
      if r[:header]                             # a group inside the tab (each fishing rod)
        fill(inner, 6 * S, y + 19 * S, 184 * S, S, [168, 168, 196])
        txt(inner, r[:header], 8 * S, y + 11 * S, 52, INK, [176, 176, 200], "l", "m")
        next
      end
      art = Art.new(0, y, 196, 25)
      b = [1, 1, 193, 22]
      art.rr([b[0] + 1, b[1] + 2, b[2] - 1, b[3] + 2], 9, 0, LAVENDER_SHADOW)
      art.rr(b, 9, 0, [56, 56, 72]).rr(b, 9, 1, [240, 240, 248]).rr(b, 9, 2, [232, 228, 248])
      art.rr([b[0] + 112, b[1] + 8, b[0] + 154, b[1] + 14], 2, 0, [56, 56, 72])
      art.rc([b[0] + 113, b[1] + 9, b[0] + 113 + [1, (40 * r[:pct].to_i / 100)].max, b[1] + 13], [96, 208, 96])
      inner << art.op
      extra = r[:seen] ? nil : { "fx" => "sil:#484860" }
      img(inner, r[:icon], 0, 0, 64, 64, (b[0] + 2) * S, y + b[1] * S - 2, 2, extra) if r[:icon]
      lv = r[:lo] == r[:hi] ? "#{l(:lv)} #{r[:lo]}" : "#{l(:lv)} #{r[:lo]}-#{r[:hi]}"
      txt(inner, r[:seen] ? r[:name] : "???", (b[0] + 26) * S, y + (b[1] + 6.5) * S, 52, INK, [176, 176, 200], "l", "m", { "fit" => 500 })
      txt(inner, lv, (b[0] + 26) * S, y + (b[1] + 15) * S, 52, [104, 104, 128], [200, 200, 220], "l", "m")
      txt(inner, "#{r[:pct]}%", (b[2] - 6) * S, y + (b[1] + 11) * S, 52, INK, [176, 176, 200], "r", "m")
    end
    view_h = 5 * 23 * S
    if rows.empty?
      txt(ops, l(:no_wild), W / 2, 90 * S, 52, INK, [176, 176, 200], "m", "m")
    else
      ops << { "o" => "scroll", "key" => "route#{d[:tab]}", "r" => [3 * S, 40 * S, 196 * S, view_h], "ch" => rows.length * 23 * S, "ops" => inner,
               "bar" => [199 * S, 43 * S, 4 * S, 110 * S], "bc1" => "#a8a8c4", "bc2" => "#585878" }
      txt(ops, l(:unseen), 8 * S, 168 * S, 52, INK, [176, 176, 200], "l", "m")
    end
    s.button(0, 0, [134, 157, 201, 178], BLUE, l(:back), "back", shadow: LAVENDER_SHADOW, center: [167, 167])
    s.button(0, 0, [106, 157, 129, 178], GREEN, nil, "home", shadow: LAVENDER_SHADOW, icon: house_icon(7, 6))
    s.result("id" => "route")
  end

  # ---- trainer cards ----

  # The game's own card screens, captured as they are drawn, at 2x: the largest whole factor that fits (3x is
  # wider than the screen, and pixels are never enlarged by a fraction).
  # d: { shot: png file, page: 1.., total: }
  def self.profile(d)
    s = Screen.new
    ops = s.ops
    bg = pic("Trainer Card/bg")
    bw, bh = size(bg)
    x0 = 54
    y0 = 38
    if bw > 0                                   # tiled like the game's plane, lined up with the tiles in the picture
      ops << { "o" => "tile", "f" => bg, "r" => [0, 0, W, H], "k" => 2, "at" => [(x0 % bw - bw) * 2, (y0 % bh - bh) * 2] }
    else
      fill(ops, 0, 0, W, H, [160, 208, 240])
    end
    whole(ops, d[:shot], x0 * 2, y0 * 2, 2) if d[:shot]
    total = d[:total].to_i
    if total > 1
      game_button(s, 6, 472, 112, 62, nil, "card:prev", nil, glyph: arrow_icon(16, 6, true))
      game_button(s, 124, 472, 112, 62, nil, "card:next", nil, glyph: arrow_icon(16, 6, false))
      txt(ops, format(l(:card), d[:page].to_i, total), 500, 1006, 52, WHITE, [72, 104, 136], "l", "m")
    end
    game_button(s, 620 - 6 - 200, 472, 200, 62, l(:back), "back")
    s.result("id" => "profile")
  end

  # ---- save ----

  # d: { done:, place:, name:, male:, id:, rows: [[label, value]...], party: [icon files], char: file }
  def self.save(d)
    s = Screen.new
    ops = s.ops
    u = 3
    lw = 414
    lh = 294
    px = (lw - 408) / 2
    py = 4
    fill(ops, 0, 0, W, H, [72, 72, 104])
    img(ops, pic("loadbg"), 0, 0, lw, lh, -1, 0, u)
    panels = pic("loadPanels")
    ys = Axis.new(536).crop(222, 444).repeat(150, 64)
    sheet(ops, panels, Axis.new(408), ys, px * u - 1, py * u, u)
    at = ->(x, y) { [(px + x) * u - 1, (py + y) * u] }
    text_c = [232, 232, 232]
    text_sh = [136, 136, 136]
    if d[:char]
      cw, chh = size(d[:char])
      cw /= 4
      chh /= 4
      img(ops, d[:char], 0, 0, cw, chh, *at.call(64 - cw / 2, 80 - chh / 2), u)
    end
    (d[:party] || []).first(6).each_with_index do |icon, i|
      img(ops, icon, 0, 0, 64, 64, *at.call(286 + 66 * (i % 2) - 32, 96 + 66 * (i / 2) - 32), u)
    end
    gtext(ops, d[:done] ? l(:saved) : l(:save_q), *at.call(32, 4), 78, text_c, text_sh)
    gtext(ops, d[:place], *at.call(386, 4), 78, text_c, text_sh, 1, { "fit" => 420 })
    name_c, name_sh = d[:male] ? [[56, 160, 248], [56, 104, 168]] : [[240, 72, 88], [160, 64, 64]]
    gtext(ops, d[:name], *at.call(112, 40), 78, name_c, name_sh)
    gtext(ops, d[:id], *at.call(112, 70), 78, text_c, text_sh)
    (d[:rows] || []).first(5).each_with_index do |(label, value), n|
      gtext(ops, label, *at.call(32, 110 + 32 * n), 78, text_c, text_sh)
      gtext(ops, value, *at.call(236, 110 + 32 * n), 78, text_c, text_sh, 1)
    end
    fill(ops, 0, lh * u - 6, W, H - lh * u + 6, [72, 72, 104])
    fill(ops, 0, lh * u - 6, W, 6, [240, 240, 248])
    sh = [40, 40, 64]
    by = H - 32 * S + 2
    if d[:done]                                 # the button stays, saying it is done; it does nothing more
      s.button(2, by, [3, 1, 128, 28], [120, 152, 128], l(:saved_button), nil, shadow: sh, center: [65, 14], disabled: true)
    else
      s.button(2, by, [3, 1, 128, 28], GREEN, upper(d[:save_label] || l(:save)), "save", shadow: sh, center: [65, 14], bounce: true)
    end
    s.button(2, by, [134, 6, 203, 28], BLUE, l(:back), "back", shadow: sh, center: [168, 17])
    s.result("id" => "save")
  end

  # ---- any other list of choices the game shows ----

  # d: { help:, options: [{label:, cmd:}], index: }
  def self.choice(d)
    s = Screen.new
    stripes(s.ops)
    opts = d[:options] || []
    n = opts.length
    cols = n > 10 ? 3 : n > 5 ? 2 : 1
    rows = (n / cols.to_f).ceil
    bw = cols == 1 ? 150 : cols == 2 ? 97 : 64
    gap = 4
    total = cols * bw + (cols - 1) * gap
    x0 = (206 - total) / 2
    pitch = rows > 5 ? 24 : 29
    high = pitch - 5
    y0 = [8, (150 - rows * pitch) / 2].max
    opts.each_with_index do |o, i|
      c = i / rows
      r = i % rows
      x = x0 + c * (bw + gap)
      y = y0 + r * pitch
      col = [PINK, ORANGE, GREEN, TEAL, BLUE, PURPLE][i % 6]
      col = BLUE if o[:kind] == :cancel
      s.button(0, 0, [x, y, x + bw - 1, y + high], col, upper(o[:label]), o[:cmd], shadow: LAVENDER_SHADOW, bounce: i == d[:index].to_i)
    end
    if d[:help] && !d[:help].to_s.empty?
      txt(s.ops, d[:help], 48, H - 150, 52, INK, [176, 176, 200], "l", "a", { "ww" => 700, "lh" => 60, "ml" => 2 })
    end
    s.button(0, 0, [134, 155, 201, 177], BLUE, l(:back), "back", shadow: LAVENDER_SHADOW, center: [167, 166])
    s.result("id" => "choice")
  end

  # Nothing to choose right now (a message, an animation): a touch anywhere is the confirm button
  def self.idle(_d = nil)
    s = Screen.new
    stripes(s.ops)
    s.result("id" => "idle", "tap" => "a")
  end
end

# Second screen (BETA): on dual-screen Android handhelds (AYN Thor...) the launcher shows DS-style touch
# menus on the other screen, in battle and in the overworld. The game stays in charge: its own menus keep
# running on the game screen and this module only (1) reports what they show, laid out for the second
# screen by AshDroidDSUI, and (2) turns a touch into what the player would do with the buttons (move the
# game's cursor and press the button it is waiting for). A few screens that the game has no menu for (home,
# region map with the wild Pokémon of the place, trainer cards, save) are run from here.
# Bridge to the launcher: Android.companion_send (state, JSON) and Android.companion_poll (touch commands).
# Without that engine API or without a second screen nothing here does anything.
module AshDroidDS
  ACTIVE_EVERY = 40 # frames between "is the second screen showing?" checks
  GRACE = 12        # frames a screen stays up after the game stops reporting it (fades, animations)
  JSON_ESC = { '"' => '\\"', "\\" => "\\\\", "\n" => "\\n", "\r" => "\\r", "\t" => "\\t" }.freeze
  BUTTONS = { use: [:USE, :C], back: [:BACK, :B], action: [:ACTION, :A], special: [:SPECIAL],
              up: [:UP], down: [:DOWN], left: [:LEFT], right: [:RIGHT] }.freeze
  SHOT_DIR = "ashdroid_ds"

  @stack = []
  @hide = false
  @active = nil
  @tick = 0
  @tickn = 0
  @seen = {}
  @modal_cmds = []
  @zoom = 2

  def self.available?
    if @available.nil?
      @available = !!(defined?(::Android) && ::Android.respond_to?(:companion_send) &&
                      ::Android.respond_to?(:companion_poll) && ::Android.respond_to?(:companion_active?))
      AshDroidLog.log("ds: engine api #{@available ? 'found' : 'missing'}")
    end
    @available
  rescue StandardError
    @available = false
  end

  def self.active?
    return false unless available?
    @tick += 1
    if @active.nil? || @tick >= ACTIVE_EVERY
      @tick = 0
      was = @active
      @active = !!::Android.companion_active?
      if @active != was
        AshDroidLog.log("ds: second screen #{@active ? 'on' : 'off'}")
        @last = nil            # a screen that just appeared needs the current state
        @key = nil
        @restore = !@active    # and a menu hidden for it must come back if it went away
      end
    end
    @active
  end

  def self.hide?
    @hide && @active
  end

  def self.json(v)
    case v
    when Hash then "{" + v.map { |k, x| json(k.to_s) + ":" + json(x) }.join(",") + "}"
    when Array then "[" + v.map { |x| json(x) }.join(",") + "]"
    when String
      s = v.encoding == Encoding::UTF_8 ? v : v.dup.force_encoding("UTF-8")
      s = s.scrub("?") unless s.valid_encoding?
      '"' + s.gsub(/["\\\x00-\x1f]/) { |c| JSON_ESC[c] || format("\\u%04x", c.ord) } + '"'
    when Integer then v.to_s
    when Float then v.finite? ? v.round(2).to_s : "0"
    when true, false then v.to_s
    when nil then "null"
    else json(v.to_s)
    end
  end

  def self.publish(state)
    return if state.equal?(@last_state) && @last   # the same screen object as last frame: nothing new
    @last_state = state
    s = json(state)
    return if s == @last
    @last = s
    ::Android.companion_send(s)
  end

  def self.log_once(tag, e)
    @errors ||= Hash.new(0)
    @errors[tag] += 1
    AshDroidLog.log("ds: #{tag} #{e.class}: #{e.message}") if @errors[tag] <= 3
  end

  def self.ui
    AshDroidDSUI
  end

  # ---- what the game is showing ----

  def self.mark(kind, obj)
    @seen[kind] = [obj, @tickn]
  end

  # The object a hook reported since the previous frame, if it did
  def self.live(kind)
    s = @seen[kind]
    s && s[1] >= @tickn - 1 ? s[0] : nil
  end

  def self.usable_window?(w)
    return false unless w && !w.disposed? && w.visible
    return false if w.respond_to?(:active) && !w.active
    c = commands_of(w)
    c.length > 0 && c.length <= 15
  rescue StandardError
    false
  end

  def self.commands_of(w)
    c = w.respond_to?(:commands) ? w.commands : w.instance_variable_get(:@commands)
    c.is_a?(Array) ? c : []
  rescue StandardError
    []
  end

  def self.label_of(c)
    (c.is_a?(Array) ? c[0] : c).to_s.gsub(/<[^>]*>/, "").strip
  end

  # What a command of the game's menus is, by its text in the current language
  def self.kind_of(label)
    @kinds = nil if @kinds_lang != ui.lang
    @kinds_lang = ui.lang
    @kinds ||= {
      _INTL("Summary") => :summary, _INTL("Item") => :item, _INTL("Use") => :use, _INTL("Give") => :give,
      _INTL("Take") => :take, _INTL("Toss") => :toss, _INTL("Switch") => :switch, _INTL("Switch In") => :switch_in,
      _INTL("Cancel") => :cancel, _INTL("Quit") => :cancel, _INTL("Exit") => :cancel, _INTL("No") => :cancel
    }
    @kinds[label]
  rescue StandardError, NameError
    nil
  end

  def self.options_of(w, skip = [])
    list = []
    commands_of(w).each_with_index do |c, i|
      label = label_of(c)
      kind = kind_of(label)
      next if skip.include?(kind)
      list << { label: label, cmd: "opt:#{i}", kind: kind, at: i }
    end
    list
  end

  # ---- game data ----

  def self.species_icon(pkmn)
    ui.file(GameData::Species.icon_filename_from_pokemon(pkmn))
  rescue StandardError, NameError
    nil
  end

  def self.item_icon(item)
    return nil unless item
    ui.file(GameData::Item.icon_filename(item))
  rescue StandardError, NameError
    nil
  end

  def self.status_row(pkmn)
    return nil if pkmn.status.nil? || pkmn.status == :NONE || pkmn.status == 0
    GameData::Status.get(pkmn.status).id_number - 1
  rescue StandardError, NameError
    nil
  end

  def self.type_row(type)
    GameData::Type.get(type).id_number.to_i
  rescue StandardError, NameError
    0
  end

  def self.mon_data(pkmn)
    return nil unless pkmn
    egg = pkmn.egg?
    gender = egg ? nil : pkmn.gender
    { name: pkmn.name.to_s, icon: species_icon(pkmn), level: pkmn.level.to_i, hp: pkmn.hp.to_i, total: pkmn.totalhp.to_i,
      gender: gender == 0 ? 0 : gender == 1 ? 1 : nil, status: egg ? nil : status_row(pkmn),
      item: egg ? nil : item_icon(pkmn.item), egg: egg }
  rescue StandardError, NameError => e
    log_once("mon", e)
    { name: "?", level: 0, hp: 0, total: 1 }
  end

  def self.mon_digest(pkmn)
    return nil unless pkmn
    [pkmn.object_id, pkmn.species, pkmn.name, pkmn.level, pkmn.hp, pkmn.totalhp, pkmn.status, pkmn.item, pkmn.egg?]
  rescue StandardError, NameError
    [pkmn.object_id]
  end

  def self.move_data(m)
    total = m.respond_to?(:total_pp) ? m.total_pp : (m.totalpp rescue 0)
    power = m.respond_to?(:base_damage) ? m.base_damage : (m.baseDamage rescue 0)
    acc = m.accuracy.to_i
    { name: m.name.to_s, type: m.type.to_s, row: type_row(m.type), cat: (m.category.to_i rescue 2),
      power: power.to_i <= 0 ? "---" : power.to_i == 1 ? "???" : power.to_i, accuracy: acc <= 0 ? "---" : "#{acc}%",
      pp: m.pp.to_i, tpp: total.to_i }
  rescue StandardError, NameError => e
    log_once("move", e)
    { name: m.name.to_s, type: "", row: 0, cat: 2, power: "---", accuracy: "---", pp: 0, tpp: 0 }
  end

  def self.sprites_of(scene)
    s = scene.instance_variable_get(:@sprites)
    s.is_a?(Hash) ? s : {}
  end

  def self.clean(text)
    text.to_s.gsub(/<[^>]*>/, "").gsub(/[\x00-\x09\x0b-\x1f]/, "").strip
  end

  # What one of a scene's text windows is saying right now ("" if it is not showing)
  def self.window_text(scene, name)
    w = sprites_of(scene)[name]
    w && !w.disposed? && w.visible && w.respond_to?(:text) ? clean(w.text) : ""
  rescue StandardError
    ""
  end

  def self.help_text(scene)
    t = window_text(scene, "messagebox")
    t.empty? ? window_text(scene, "helpwindow") : t
  end

  # ---- the game screen keeps what it was showing ----
  # While the bag or the party run on the second screen, a picture of what the game screen showed before
  # (the battle, the map) covers the game's own copy of that menu. It steps aside whenever something else
  # needs the game screen: the summary, a number to enter, any screen this module does not show.

  def self.cover_begin
    return unless @active
    @cover_depth = @cover_depth.to_i + 1
    return if @cover
    @cover_vp = Viewport.new(0, 0, Graphics.width, Graphics.height)
    @cover_vp.z = 9_999_999
    @cover = Sprite.new(@cover_vp)
    @cover.bitmap = Graphics.snap_to_bitmap
    @cover_seen = @tickn
  rescue StandardError, NameError => e
    log_once("cover", e)
    cover_drop
  end

  def self.cover_end
    return if @cover_depth.to_i <= 0
    @cover_depth -= 1
    cover_drop if @cover_depth <= 0
  end

  def self.cover_drop
    @cover_depth = 0
    [(@cover.bitmap rescue nil), @cover, @cover_vp].each { |o| o.dispose if o && !o.disposed? rescue nil }
    @cover = @cover_vp = nil
  end

  def self.cover_update
    return unless @cover
    if @cover.disposed?
      cover_drop
      return
    end
    bag = live(:bag)
    # something that is only on the game's screen: the summary, or the bag asking for a number
    blocked = live(:summary) ? true : false
    if !blocked && bag && !usable_window?(live(:cmd))
      blocked = !window_text(bag, "helpwindow").empty? && window_text(bag, "msgwindow").empty?
    end
    if blocked
      @cover.visible = false
      @cover_seen = -1000
    elsif bag || live(:party)
      @cover.visible = true
      @cover_seen = @tickn
    else
      # between two of the game's screens (their fades): stay up, so the game screen never flashes the menu;
      # if nothing known shows up within a second, something else needs the game screen
      @cover.visible = @tickn - @cover_seen <= 40
    end
  rescue StandardError, NameError => e
    log_once("cover", e)
    cover_drop
  end

  # ---- every frame (Graphics.update) ----

  def self.tick
    return unless available?
    return if @in_tick
    @in_tick = true
    if active?
      ui.lang = (AshDroidText.art_lang rescue nil) || "en" if @tickn % 60 == 0 || @lang_set.nil?
      @lang_set = true
      8.times do
        cmd = ::Android.companion_poll
        break unless cmd
        handle(cmd.to_s)
      end
      pursue
      cover_update
      state = compose
      publish(state) if state
    end
    @tickn += 1
  rescue StandardError, NameError => e
    log_once("tick", e)
  ensure
    @in_tick = false
  end

  # Builds the screen only when what it shows may have changed
  def self.screen(ctx, key)
    key = [ctx, ui.lang, key]
    @ctx = ctx
    @idle_since = nil
    return @state if key == @key && @state
    @key = key
    @state = yield
  end

  def self.compose
    return modal_state if @modal
    cw = live(:cmd)
    cw = nil unless usable_window?(cw)
    @auto = nil if @auto && @tickn > @auto[1]
    if cw && @auto
      i = commands_of(cw).index { |c| label_of(c) == @auto[0] }
      @auto = nil
      if i
        cw.index = i
        force(:use)
        return nil
      end
    end
    party = live(:party)
    bag = live(:bag)
    summary = live(:summary)
    battle = live(:battle)
    if cw && party then party_actions_state(party, cw)
    elsif cw && bag then bag_state(bag, cw)
    elsif cw then choice_state(cw)
    elsif summary then summary_state(summary)
    elsif bag then bag_state(bag, nil)
    elsif party then party_state(party)
    elsif battle && battle_state(battle) then @state
    elsif live(:world) && !$game_temp.in_menu then home_state
    else
      # nothing reported: keep the last screen through fades and animations, then a plain "touch = confirm"
      @idle_since ||= @tickn
      return nil if @state && @tickn - @idle_since < GRACE
      @ctx = :idle
      @key = nil
      @state = (@idle_state ||= ui.idle)
    end
  rescue StandardError, NameError => e
    log_once("compose", e)
    nil
  end

  # ---- touches ----

  def self.handle(cmd)
    case cmd
    when "hello"
      @last = nil
      @last_state = nil
      @key = nil
      return
    when /\Ahide:([01])\z/
      @hide = $1 == "1"
      return
    end
    if @modal
      @modal_cmds << cmd
      return
    end
    case cmd
    when "a" then force(:use)
    when "back"
      @moves_view = false if @ctx == :moves
      force(:back)
    when "home" then @unwind = 40
    when "act" then force(:action) if @ctx == :fight
    when "shift" then force(:special) if @ctx == :fight
    when /\Asel:(\d+)\z/ then select($1.to_i)
    when /\Aopt:(\d+)\z/
      cw = live(:cmd)
      if usable_window?(cw) && $1.to_i < commands_of(cw).length
        cw.index = $1.to_i
        force(:use)
      end
    when /\Aswap:(\d+):(\d+)\z/ then swap_party($1.to_i, $2.to_i)
    when /\Ado:(\d+)\z/                         # an action of the sheet: choose the Pokémon, then that option
      o = @ctx == :sheet && @sheet_options ? @sheet_options[$1.to_i] : nil
      if o && live(:party)
        @auto = [o[:label], @tickn + 45]
        force(:use)
      end
    when "mon:prev", "mon:next"
      scene = live(:party)
      if scene && @ctx == :sheet
        party = scene.instance_variable_get(:@party) || []
        n = scene.instance_variable_get(:@activecmd).to_i + (cmd == "mon:next" ? 1 : -1)
        if n >= 0 && n < party.length
          scene.pbSelect(n)
          pbPlayCursorSE
        end
      end
    when /\Amswap:(\d+):(\d+)\z/ then swap_moves($1.to_i, $2.to_i)
    when /\Apocket:(\d+)\z/ then @goal = [:pocket, $1.to_i, 10] if live(:bag)
    when /\Aitem:(\d+)\z/ then pick_item($1.to_i)
    when "use"
      if @ctx == :bag && live(:bag)
        cw = live(:cmd)
        use = usable_window?(cw) ? commands_of(cw).index { |c| kind_of(label_of(c)) == :use } : nil
        if use                              # the game is already asking what to do with the item
          cw.index = use
        else
          @auto = [_INTL("Use"), @tickn + 45]
        end
        force(:use)
      end
    when "sum:prev" then force(:up) if live(:summary)
    when "sum:next" then force(:down) if live(:summary)
    when /\Apage:(\d)\z/
      if live(:summary)
        @goal = [:page, $1.to_i + 1, 8]
        @moves_view = $1.to_i == 3
      end
    when "moves:close" then @moves_view = false
    when /\Aopen:(\w+)\z/
      if @ctx == :home
        @open = $1.to_sym
        @open_wait = 0
      end
    end
  rescue StandardError, NameError => e
    log_once("handle", e)
  end

  def self.select(n)
    case @ctx
    when :command, :fight
      scene, cw = live(:battle)
      return unless scene && cw && n < @count.to_i
      cw.index = n if cw.index != n
      force(:use)
    when :party
      scene = live(:party)
      return unless scene
      party = scene.instance_variable_get(:@party)
      return unless party && party[n]
      scene.pbSelect(n)
      force(:use)
    end
  end

  # Things that take several button presses: one press every few frames until the game gets there
  def self.pursue
    if @unwind
      @unwind -= 1
      done = @unwind <= 0 || live(:world) || live(:battle)
      if done
        @unwind = nil
      elsif !@forced && @tickn % 4 == 0
        force(:back)
      end
      return
    end
    return unless @goal && !@forced && @tickn % 3 == 0
    kind, target, tries = @goal
    @goal = tries > 1 ? [kind, target, tries - 1] : nil
    case kind
    when :pocket
      scene = live(:bag)
      win = scene && sprites_of(scene)["itemlist"]
      if !win || win.pocket == target
        @goal = nil
      else
        n = (PokemonBag.numPockets rescue 8)
        force((target - win.pocket) % n <= n / 2 ? :right : :left)
      end
    when :page
      scene = live(:summary)
      page = scene && scene.instance_variable_get(:@page)
      if !page || page == target
        @goal = nil
      else
        force(target > page ? :right : :left)
      end
    end
  rescue StandardError, NameError => e
    @goal = nil
    log_once("pursue", e)
  end

  # ---- a touch becomes the button press the menu is waiting for ----

  def self.force(name)
    b = BUTTONS[name].map { |c| Input.const_defined?(c) ? Input.const_get(c) : nil }.compact.first
    @forced = [b, Graphics.frame_count] if b
  end

  def self.forced?(button)
    f = @forced
    return false unless f
    if (Graphics.frame_count - f[1]).abs > 3
      @forced = nil
      return false
    end
    return false unless f[0] == button
    @forced = nil
    true
  rescue StandardError
    false
  end

  def self.forcing?
    !@forced.nil?
  end

  # ---- battle ----

  def self.enter(scene, mode, data)
    return unless available?
    @stack.clear unless @scene.equal?(scene)
    @scene = scene
    @prompt = nil if mode == :command
    @stack.push(data.merge(mode: mode))
  rescue StandardError => e
    log_once("enter", e)
  end

  def self.leave(mode)
    return unless available?
    i = @stack.rindex { |d| d[:mode] == mode }
    @stack.slice!(i..-1) if i
  rescue StandardError
  end

  def self.window(scene, mode)
    sprites_of(scene)[mode == :fight ? "fightWindow" : "commandWindow"]
  end

  def self.top_for(scene)
    @scene.equal?(scene) ? @stack.last : nil
  end

  # Called every frame a battle menu is open
  def self.frame(scene, cw)
    unless @active
      if @restore
        @restore = false
        if cw && @stack.any? && !cw.visible
          cw.visible = true
          unprompt(scene)
        end
      end
      return
    end
    top = top_for(scene)
    mine = top && cw && cw.equal?(window(scene, top[:mode]))
    return unless mine
    mark(:battle, [scene, cw])
    if @hide
      cw.visible = false if cw.visible
      prompt(scene)
    elsif !cw.visible
      cw.visible = true
      unprompt(scene)
    end
  rescue StandardError, NameError => e
    log_once("frame", e)
  end

  # With the game's menu hidden, its message bar says "What will X do?" full width instead of staying empty
  def self.prompt(scene)
    entry = @stack.reverse.find { |d| d[:mode] == :command }
    title = entry && Array(entry[:texts])[0].to_s
    return if title.nil? || title.empty?
    s = sprites_of(scene)
    box = s["messageBox"]
    win = s["messageWindow"]
    return unless win && !win.disposed?
    box.visible = true if box && !box.disposed? && !box.visible
    win.visible = true unless win.visible
    if @prompt != [win.object_id, title] || win.text.to_s.empty?
      @prompt = [win.object_id, title]
      win.text = title
    end
    win.update if win.respond_to?(:busy?) && win.busy?
  rescue StandardError, NameError => e
    log_once("prompt", e)
  end

  def self.unprompt(scene)
    return unless @prompt
    @prompt = nil
    s = sprites_of(scene)
    %w[messageBox messageWindow].each { |k| s[k].visible = false if s[k] && !s[k].disposed? }
  rescue StandardError, NameError
  end

  def self.dots(party)
    party.map do |p|
      next nil unless p
      next :ko if p.fainted?
      p.status && p.status != :NONE && p.status != 0 ? :st : :ok
    end
  rescue StandardError, NameError
    []
  end

  # 0 no effect, 1 not very effective, 2 super effective; nil for a normal hit, a status move,
  # or more than one opponent
  def self.effectiveness(battle, battler, move)
    return nil if move.respond_to?(:statusMove?) && move.statusMove?
    foes = battle.battlers.select { |b| b && !b.fainted? && b.opposes?(battler.index) }
    return nil unless foes.length == 1
    mod = move.pbCalcTypeMod(move.pbCalcType(battler), battler, foes[0])
    return 0 if Effectiveness.ineffective?(mod)
    return 1 if Effectiveness.not_very_effective?(mod)
    return 2 if Effectiveness.super_effective?(mod)
    nil
  rescue StandardError, NameError
    nil
  end

  # A colour of one of the game's button images: the first of the given points that is neither grey nor dark
  def self.sample(bitmap, points)
    points.each do |x, y|
      c = bitmap.get_pixel(x, y)
      rgb = [c.red.to_i, c.green.to_i, c.blue.to_i]
      return rgb if c.alpha.to_i > 200 && rgb.max - rgb.min > 40
    end
    nil
  rescue StandardError
    nil
  end

  # [plain, under the cursor] for each button of one of the game's battle menus, read from its own image
  def self.button_colors(cw)
    @button_colors ||= {}
    list = cw.instance_variable_get(:@buttons)
    return [] unless list.is_a?(Array)
    list.map do |b|
      next nil unless b && !b.disposed? && b.bitmap && !b.bitmap.disposed?
      bmp = b.bitmap
      y = b.src_rect.y.to_i
      w = bmp.width / 2
      @button_colors[[bmp.object_id, y]] ||= [
        sample(bmp, [[16, y + 10], [14, y + 8], [20, y + 10], [16, y + 12]]),
        sample(bmp, [[w + 16, y + 12], [w + 8, y + 22], [w + 6, y + 22], [w + 12, y + 36], [w + 10, y + 10]])
      ]
    end
  rescue StandardError, NameError => e
    log_once("colors", e)
    []
  end

  def self.battle_state(pair)
    scene, cw = pair
    top = top_for(scene)
    return nil unless top && cw && !cw.disposed?
    battle = scene.instance_variable_get(:@battle)
    if top[:mode] == :fight
      battler = battle.battlers[top[:battler]]
      mode = top[:mega] && cw.respond_to?(:mode) ? cw.mode.to_i : 0
      key = [top.object_id, cw.index, mode, battler.moves.map { |m| m && [m.id, m.pp] }]
      screen(:fight, key) do
        cols = button_colors(cw)
        moves = battler.moves.compact.select { |m| m.id }.each_with_index.map do |m, i|
          plain, light = cols[i]
          move_data(m).merge(eff: effectiveness(battle, battler, m), col: plain, light: light)
        end
        @count = moves.length
        ui.fight(moves: moves, index: cw.index.to_i, mega: mode, cancel: _INTL("Cancel"))
      end
    else
      texts = Array(top[:texts]).map(&:to_s)
      own = dots(battle.pbParty(0))
      foe = battle.wildBattle? ? [:ok] : dots(battle.pbParty(1))
      screen(:command, [top.object_id, cw.index, texts, own, foe]) do
        @count = 4
        rows = (cw.instance_variable_get(:@buttons) || []).first(4).map { |b| (b.src_rect.y.to_i / 46 rescue nil) }
        ui.command(texts: texts[1, 4] || [], index: cw.index.to_i, own: own, foe: foe, rows: rows)
      end
    end
  rescue StandardError, NameError => e
    log_once("battle", e)
    nil
  end

  # Prepended to the battle scene (PokeBattle_Scene / Battle::Scene)
  module Scene
    def pbCommandMenuEx(idxBattler, texts, *args)
      AshDroidDS.enter(self, :command, texts: texts)
      super
    ensure
      AshDroidDS.leave(:command)
    end

    def pbFightMenu(idxBattler, *args, &block)
      AshDroidDS.enter(self, :fight, battler: idxBattler, mega: args[0] ? true : false)
      super
    ensure
      AshDroidDS.leave(:fight)
    end

    def pbFrameUpdate(cw = nil)
      super
    ensure
      AshDroidDS.frame(self, cw)
    end

    # The bag and the party opened from a battle go to the second screen; this one keeps showing the battle
    def pbItemMenu(*args, &block)
      AshDroidDS.cover_begin
      super
    ensure
      AshDroidDS.cover_end
    end

    def pbPartyScreen(*args, &block)
      AshDroidDS.cover_begin
      super
    ensure
      AshDroidDS.cover_end
    end

    # The menu was just made visible: with "hide" on it never gets drawn on the game's screen
    def pbShowWindow(windowType)
      super
    ensure
      begin
        if AshDroidDS.hide?
          top = AshDroidDS.top_for(self)
          w = top && AshDroidDS.window(self, top[:mode])
          w.visible = false if w && w.visible
        end
      rescue StandardError
      end
    end
  end

  # ---- party ----

  # What the game's party menu offers for a Pokémon, as its "Do what with X?" list will (see pbPokemonScreen)
  def self.party_options(party, pkmn)
    list = [{ label: _INTL("Summary"), kind: :summary }]
    unless pkmn.egg?
      pkmn.moves.each do |m|
        next unless m && m.id
        next unless [:MILKDRINK, :SOFTBOILED].include?(m.id) || HiddenMoveHandlers.hasHandler(m.id)
        list << { label: m.name.to_s }
      end
    end
    list << { label: _INTL("Switch"), kind: :switch } if party.length > 1
    unless pkmn.egg?
      if pkmn.mail
        list << { label: _INTL("Mail") }
      elsif !($game_switches[675] rescue false)
        list << { label: _INTL("Item"), kind: :item }
      end
    end
    list.each_with_index { |o, i| o[:cmd] = "do:#{i}" }
    @sheet_options = list
  rescue StandardError, NameError => e
    log_once("options", e)
    @sheet_options = []
  end

  def self.switching?(scene)
    spr = sprites_of(scene)["pokemon0"]
    spr && spr.instance_variable_get(:@switching) ? true : false
  rescue StandardError
    false
  end

  # On the overworld the game's party screen is on the game screen: the second screen shows the sheet of
  # the Pokémon under its cursor
  def self.sheet_state(scene, party, sel)
    pkmn = party[sel]
    key = [scene.object_id, sel, party.length, mon_digest(pkmn), pkmn.moves.map { |m| m && [m.id, m.pp] }]
    screen(:sheet, key) do
      ui.party_sheet(mon: mon_data(pkmn).merge(item: item_icon(pkmn.item)), slot: sel, first: sel <= 0, last: sel >= party.length - 1,
                     types: (pkmn.egg? ? [] : (pkmn.types rescue [])).map { |t| type_row(t) },
                     item_name: pkmn.item ? GameData::Item.get(pkmn.item).name.to_s : "",
                     moves: pkmn.egg? ? [] : pkmn.moves.compact.map { |m| move_data(m) }, options: party_options(party, pkmn))
    end
  end

  def self.party_state(scene)
    party = scene.instance_variable_get(:@party) || []
    sel = scene.instance_variable_get(:@activecmd).to_i
    if @party_free && !$game_temp.in_battle && party[sel] && window_text(scene, "messagebox").empty? && !switching?(scene)
      return sheet_state(scene, party, sel)
    end
    help = help_text(scene)
    drag = false
    message = !window_text(scene, "messagebox").empty?
    key = [scene.object_id, sel, help, drag, message, party.map { |p| mon_digest(p) }]
    screen(:party, key) do
      ui.party(mons: party.first(6).map { |p| mon_data(p) }, sel: sel, help: help, drag: drag && !message, message: message,
               home: !$game_temp.in_battle && @from_home != :party)
    end
  end

  # The game asks what to do with the chosen Pokémon
  def self.party_actions_state(scene, cw)
    party = scene.instance_variable_get(:@party) || []
    slot = scene.instance_variable_get(:@activecmd).to_i
    pkmn = party[slot]
    return choice_state(cw) unless pkmn
    in_battle = $game_temp.in_battle
    drag = @party_free && !in_battle
    key = [scene.object_id, cw.object_id, cw.index, slot, mon_digest(pkmn), commands_of(cw).map { |c| label_of(c) }, in_battle]
    screen(:actions, key) do
      opts = options_of(cw, [:cancel])
      mon = mon_data(pkmn)
      sw = opts.find { |o| o[:kind] == :switch_in }
      if in_battle && (sw || opts.all? { |o| o[:kind] == :summary })
        ui.battle_info(mon: mon, slot: slot, types: (pkmn.types rescue []).map { |t| type_row(t) },
                       moves: pkmn.moves.compact.map { |m| move_data(m) }, can_switch: !sw.nil?, switch_cmd: sw && sw[:cmd])
      else
        help = help_text(scene)
        if opts.any? { |o| [:give, :take].include?(o[:kind]) }
          help = pkmn.item ? format(ui.l(:held), GameData::Item.get(pkmn.item).name) : ui.l(:no_held)
        end
        index = opts.index { |o| o[:at] == cw.index.to_i } || -1
        ui.party_actions(mon: mon, slot: slot, options: opts, index: index, help: help)
      end
    end
  end

  def self.swap_party(a, b)
    scene = live(:party)
    return unless scene && @party_free && !$game_temp.in_battle && a != b
    party = scene.instance_variable_get(:@party)
    return unless party && party[a] && party[b]
    party[a], party[b] = party[b], party[a]
    scene.pbHardRefresh
    scene.pbSelect(b)
    pbSEPlay("GUI party switch") rescue nil
    @key = nil
  end

  # ---- summary ----

  def self.summary_state(scene)
    pkmn = scene.instance_variable_get(:@pokemon)
    return ui.idle unless pkmn
    page = scene.instance_variable_get(:@page).to_i
    party = scene.instance_variable_get(:@party) || []
    idx = scene.instance_variable_get(:@partyindex).to_i
    in_battle = scene.instance_variable_get(:@inbattle)
    @moves_view = false if page != 4 && !(@goal && @goal[0] == :page)
    if @moves_view && page == 4 && !pkmn.egg?
      key = [scene.object_id, pkmn.object_id, pkmn.moves.map { |m| m && [m.id, m.pp] }]
      screen(:moves, key) do
        ui.moves(name: pkmn.name.to_s, icon: species_icon(pkmn), moves: pkmn.moves.compact.map { |m| move_data(m) },
                 drag: !in_battle && pkmn.moves.compact.length > 1)
      end
    else
      screen(:summary, [scene.object_id, pkmn.object_id, pkmn.name, page, idx, party.length]) do
        ui.summary(name: pkmn.name.to_s, icon: species_icon(pkmn), page: page - 1, first: idx <= 0,
                   last: idx >= party.length - 1, egg: pkmn.egg?)
      end
    end
  end

  def self.swap_moves(a, b)
    scene = live(:summary)
    return unless scene && @ctx == :moves && a != b
    return if scene.instance_variable_get(:@inbattle)
    sel = sprites_of(scene)["movesel"]
    return if sel && !sel.disposed? && sel.visible    # the game is in its own move selection
    pkmn = scene.instance_variable_get(:@pokemon)
    return unless pkmn && pkmn.moves[a] && pkmn.moves[b]
    pkmn.moves[a], pkmn.moves[b] = pkmn.moves[b], pkmn.moves[a]
    scene.drawPage(4)
    pbSEPlay("GUI party switch") rescue nil
    @key = nil
  end

  # ---- bag ----

  def self.bag_items(scene, win)
    bag = scene.instance_variable_get(:@bag)
    filter = win.instance_variable_get(:@filterlist)
    pocket = bag.pockets[win.pocket] || []
    filter ? (filter[win.pocket] || []).map { |j| pocket[j] }.compact : pocket
  end

  def self.item_name(id)
    @adapter ||= PokemonMartAdapter.new
    @adapter.getDisplayName(id).to_s
  rescue StandardError, NameError
    GameData::Item.get(id).name.to_s
  end

  def self.bag_state(scene, cw)
    win = sprites_of(scene)["itemlist"]
    return ui.idle unless win && !win.disposed?
    items = bag_items(scene, win)
    index = win.index.to_i
    in_battle = $game_temp.in_battle
    state = in_battle ? :battle : cw ? :item : :world
    labels = cw ? commands_of(cw).map { |c| label_of(c) } : nil
    message = window_text(scene, "msgwindow")
    key = [scene.object_id, win.pocket, index, state, items.map { |i| [i[0], i[1]] }, labels, cw && cw.index, message]
    screen(:bag, key) do
      current = items[index]
      rows = items.map do |id, qty|
        data = GameData::Item.get(id)
        { icon: item_icon(id), name: item_name(id), qty: data.is_important? ? nil : qty.to_i }
      end
      opts = cw ? options_of(cw) : []
      ui.bag(pocket: win.pocket, pockets: (PokemonBag.numPockets rescue 8), items: rows, index: index,
             desc: current ? GameData::Item.get(current[0]).description.to_s : "", state: state, options: opts,
             opt_index: cw ? (opts.index { |o| o[:at] == cw.index.to_i } || -1) : -1,
             can_use: !current.nil?, use_label: _INTL("Use"), message: message)
    end
  end

  def self.pick_item(i)
    scene = live(:bag)
    return unless scene && @ctx == :bag && !live(:cmd) && window_text(scene, "msgwindow").empty?
    win = sprites_of(scene)["itemlist"]
    return unless win && i < bag_items(scene, win).length
    win.index = i
    force(:use) unless $game_temp.in_battle   # in battle a touch only moves the cursor; USAR confirms
  end

  # ---- any other list of choices ----

  def self.choice_state(cw)
    labels = commands_of(cw).map { |c| label_of(c) }
    screen(:choice, [cw.object_id, cw.index, labels]) do
      ui.choice(help: "", options: options_of(cw), index: cw.index.to_i)
    end
  end

  # ---- overworld ----

  def self.player_free?
    return false if $game_temp.in_menu || $game_temp.in_battle || $game_temp.message_window_showing
    return false if $game_system.map_interpreter.running? || $game_system.menu_disabled
    return false if $game_player.moving? || $game_player.move_route_forcing
    true
  rescue StandardError, NameError
    false
  end

  # The home buttons stay lit while the player merely walks: only an event, a message or a closed menu
  # greys them out (a touch made mid-step waits for the step to end)
  def self.home_usable?
    return false if $game_temp.in_menu || $game_temp.in_battle || $game_temp.message_window_showing
    return false if $game_system.map_interpreter.running? || $game_system.menu_disabled
    return false if $game_player.move_route_forcing
    true
  rescue StandardError, NameError
    false
  end

  def self.home_state(cursor = nil)
    party = $Trainer.party
    free = cursor ? true : home_usable?
    off = []
    off << 0 if party.empty?
    off << 1 if (pbInBugContest? rescue false) || ($game_switches[675] rescue false)
    off << 4 if $game_system.save_disabled
    time = (pbGetTimeNow.strftime("%H:%M") rescue Time.now.strftime("%H:%M"))
    key = [$game_map.map_id, time, free, cursor, off, party.map { |p| mon_digest(p) }]
    screen(:home, key) do
      ui.home(place: $game_map.name.to_s, time: time, mons: party.first(6).map { |p| mon_data(p) }, trainer: $Trainer.name.to_s,
              free: free, off: off, cursor: cursor, save_label: _INTL("Save"), bag_label: _INTL("Bag"), map_label: _INTL("Map"))
    end
  end

  # Called every frame of the map scene
  def self.world_frame(scene)
    return unless @active
    mark(:world, scene)
    open = nil
    return unless @open
    @open_wait = @open_wait.to_i + 1
    if !home_usable? || @open_wait > 40      # an event started meanwhile, or the player never stopped
      @open = nil
      @open_wait = 0
      return
    end
    return unless player_free?
    open = @open
    @open = nil
    @open_wait = 0
    run_open(open)
  rescue StandardError, NameError => e
    log_once("world", e)
  ensure
    $game_temp.in_menu = false if open && $game_temp
  end

  def self.run_open(what)
    case what
    when :party
      return if $Trainer.party.empty?
      pbPlayDecisionSE
      $game_temp.in_menu = true
      hidden = nil
      @from_home = :party
      pbFadeOutIn {
        sscene = PokemonParty_Scene.new
        sscreen = PokemonPartyScreen.new(sscene, $Trainer.party)
        hidden = sscreen.pbPokemonScreen
      }
      @from_home = nil
      $game_temp.in_menu = false
      pbUseHiddenMove(hidden[0], hidden[1]) if hidden
    when :bag
      pbPlayDecisionSE
      $game_temp.in_menu = true
      item = nil
      begin
        cover_begin                             # the game screen keeps showing the map
        scene = PokemonBag_Scene.new
        bag = (defined?($bag) && $bag) ? $bag : $PokemonBag
        item = PokemonBagScreen.new(scene, bag).pbStartScreen
      ensure
        cover_end
      end
      $game_temp.in_menu = false
      pbUseKeyItemInField(item) if item
    when :map then modal_map
    when :card then modal_cards
    when :save then modal_save unless $game_system.save_disabled
    end
  ensure
    @from_home = nil
    $game_temp.in_menu = false
    @key = nil
  end

  # The menu button: the cursor goes to the second screen's home. Returns false to let the game's own
  # pause menu open instead (second press, or no second screen).
  def self.take_menu?
    return false unless @active && !@modal && !@want_game_menu
    true
  rescue StandardError
    false
  end

  def self.home_cursor
    $game_temp.menu_calling = false
    $game_temp.menu_beep = false
    $game_temp.in_menu = true
    pbPlayDecisionSE
    cursor = 0
    choice = nil
    modal(:home) do |cmd|
      @modal_view = -> { home_state(cursor) }
      if Input.trigger?(Input::UP) then cursor = (cursor - 1) % 5; pbPlayCursorSE
      elsif Input.trigger?(Input::DOWN) then cursor = (cursor + 1) % 5; pbPlayCursorSE
      elsif Input.trigger?(Input::USE) then choice = %i[party bag map card save][cursor]
      elsif Input.trigger?(Input::ACTION) || Input.trigger?(Input::SPECIAL) then choice = :menu
      elsif cmd =~ /\Aopen:(\w+)\z/ then choice = $1.to_sym
      end
      next :done if choice || cmd == "back"
    end
    $game_temp.in_menu = false
    if choice == :menu
      @want_game_menu = true
      $game_temp.menu_calling = true
    elsif choice
      run_open(choice)
    else
      pbPlayCloseMenuSE
    end
  rescue StandardError, NameError => e
    log_once("home", e)
  ensure
    $game_temp.in_menu = false
  end

  # ---- screens run from here (the game keeps showing the map) ----

  def self.modal(kind)
    @modal = kind
    @modal_cmds.clear
    @modal_exit = false
    @key = nil
    loop do
      Graphics.update
      Input.update
      pbUpdateSceneMap
      cmd = @modal_cmds.shift
      if cmd == "home"
        @modal_exit = true
        break
      end
      cmd ||= "back" if Input.trigger?(Input::BACK)
      break if yield(cmd) == :done || @modal_exit
    end
  ensure
    @modal = nil
    @modal_view = nil
    @key = nil
    Input.update
  end

  def self.modal_state
    @modal_view ? @modal_view.call : nil
  rescue StandardError, NameError => e
    log_once("modal", e)
    nil
  end

  def self.map_info
    meta = GameData::MapMetadata.try_get($game_map.map_id)
    pos = meta ? meta.town_map_position : nil
    region = pos ? pos[0] : (pbGetCurrentRegion(0) rescue 0)
    data = pbLoadTownMapData[region]
    name = (pbGetMessage(MessageTypes::RegionNames, region) rescue data[0]).to_s
    marker = ui.file(GameData::TrainerType.player_map_icon_filename($Trainer.trainer_type)) rescue nil
    { region: name, place: $game_map.name.to_s, map: ui.pic(data[1]), marker: pos ? marker : nil,
      pos: pos ? [pos[1], pos[2]] : [-9, -9] }
  rescue StandardError, NameError => e
    log_once("map", e)
    { region: "", place: ($game_map.name.to_s rescue ""), map: nil, marker: nil, pos: [-9, -9] }
  end

  def self.wild_tabs
    data = GameData::Encounter.get($game_map.map_id, $PokemonGlobal.encounter_version)
    return [] unless data
    tabs = {}
    data.types.each do |type, slots|
      next if slots.nil? || slots.empty?
      kind = (GameData::EncounterType.get(type).type rescue :land)
      next if kind == :contest
      label, col = case kind
                   when :cave then [ui.l(:cave), [184, 160, 56]]
                   when :water then [(GameData::Move.get(:SURF).name rescue "Surf"), ui::BLUE]
                   when :fishing then [ui.l(:fishing), ui::TEAL]
                   else [ui.l(:grass), ui::GREEN]
                   end
      total = slots.inject(0) { |a, s| a + s[0].to_i }
      merged = {}
      slots.each do |chance, species, lo, hi|
        hi ||= lo
        e = (merged[species] ||= [0, lo, hi])
        e[0] += chance.to_i
        e[1] = [e[1], lo].min
        e[2] = [e[2], hi].max
      end
      rows = merged.sort_by { |_, v| -v[0] }.map do |species, (chance, lo, hi)|
        seen = ($Trainer.pokedex.seen?(species) rescue true)
        { icon: (ui.file(GameData::Species.icon_filename(species)) rescue nil), name: (GameData::Species.get(species).name.to_s rescue species.to_s),
          seen: seen, lo: lo, hi: hi, pct: total > 0 ? [(chance * 100.0 / total).round, 1].max : 0 }
      end
      tab = (tabs[kind] ||= { label: label, col: col, rows: [] })
      # one tab per way of finding them; inside "fishing", a heading per rod
      tab[:rows] << { header: (GameData::Item.get(type.to_s.upcase.to_sym).name.to_s rescue type.to_s) } if kind == :fishing
      tab[:rows].concat(rows)
    end
    tabs = tabs.values
    tabs.first(4)
  rescue StandardError, NameError => e
    log_once("wild", e)
    []
  end

  def self.modal_map
    pbPlayDecisionSE
    $game_temp.in_menu = true
    info = map_info
    tabs = wild_tabs
    wild = false
    tab = 0
    modal(:map) do |cmd|
      @modal_view = lambda do
        if wild
          screen(:route, [tab, ui.lang]) { ui.route(place: info[:place], tabs: tabs, tab: tab, rows: tabs[tab][:rows]) }
        else
          screen(:map, [@zoom, tabs.length]) { ui.region_map(info.merge(zoom: @zoom, wild: !tabs.empty?)) }
        end
      end
      if wild
        if cmd == "back" then wild = false; pbPlayCloseMenuSE
        elsif cmd =~ /\Atab:(\d)\z/ then tab = [$1.to_i, tabs.length - 1].min
        elsif Input.trigger?(Input::RIGHT) then tab = (tab + 1) % tabs.length; pbPlayCursorSE
        elsif Input.trigger?(Input::LEFT) then tab = (tab - 1) % tabs.length; pbPlayCursorSE
        end
        next
      end
      next :done if cmd == "back"
      if cmd =~ /\Azoom:(\d)\z/ then @zoom = [[$1.to_i, 2].max, 4].min
      elsif (cmd == "wild" || Input.trigger?(Input::USE)) && !tabs.empty? then wild = true; pbPlayDecisionSE
      elsif Input.trigger?(Input::UP) then @zoom = [@zoom + 1, 4].min
      elsif Input.trigger?(Input::DOWN) then @zoom = [@zoom - 1, 2].max
      end
    end
    pbPlayCloseMenuSE unless @modal_exit
  ensure
    $game_temp.in_menu = false
  end

  # A picture of one of the game's card screens, taken behind a picture of the map so the game screen
  # never shows it. page 1: the trainer card; 2..5: the four pages of the other card, if the player has it.
  def self.card_shot(page)
    Dir.mkdir(SHOT_DIR) unless File.directory?(SHOT_DIR)
    vp = Viewport.new(0, 0, Graphics.width, Graphics.height)
    vp.z = 9_999_999
    cover = Sprite.new(vp)
    cover.bitmap = Graphics.snap_to_bitmap
    scene = nil
    if page == 1
      scene = PokemonTrainerCard_Scene.new
      scene.pbStartScene
    else
      scene = GCCard_Scene.new
      scene.send(%i[pbDrawGCCardOne pbDrawGCCardTwo pbDrawGCCardThree pbDrawGCCardFour][page - 2])
    end
    cover.visible = false
    shot = Graphics.snap_to_bitmap
    cover.visible = true
    path = File.expand_path(File.join(SHOT_DIR, "card#{page}_#{Graphics.frame_count}.png"))
    Dir.glob(File.join(SHOT_DIR, "card#{page}_*.png")).each { |f| File.delete(f) rescue nil }
    shot.to_file(path)
    shot.dispose
    path
  rescue StandardError, NameError => e
    log_once("card", e)
    nil
  ensure
    begin
      if scene
        sprites = scene.instance_variable_get(:@sprites)
        pbDisposeSpriteHash(sprites) if sprites
        old = scene.instance_variable_get(:@oldOutfit)
        $Trainer.outfit = old if old
        svp = scene.instance_variable_get(:@viewport)
        svp.dispose if svp && !svp.disposed?
      end
    rescue StandardError, NameError
    end
    [(cover.bitmap rescue nil), cover, vp].each { |o| o.dispose if o && !o.disposed? rescue nil }
  end

  def self.modal_cards
    pbSEPlay("GUI trainer card open") rescue nil
    $game_temp.in_menu = true
    has_other = (defined?(GCCard_Scene) && $PokemonBag.pbHasItem?(:GCCARD)) rescue false
    total = has_other ? 5 : 1
    page = 1
    shots = {}
    modal(:cards) do |cmd|
      shots[page] ||= card_shot(page) || ""
      @modal_view = -> { screen(:cards, [page, shots[page]]) { ui.profile(shot: shots[page].to_s.empty? ? nil : shots[page], page: page, total: total) } }
      next :done if cmd == "back"
      if total > 1
        if cmd == "card:next" || Input.trigger?(Input::RIGHT) then page = page % total + 1; pbPlayCursorSE
        elsif cmd == "card:prev" || Input.trigger?(Input::LEFT) then page = (page - 2) % total + 1; pbPlayCursorSE
        end
      end
    end
    pbPlayCloseMenuSE unless @modal_exit
  ensure
    $game_temp.in_menu = false
  end

  def self.save_data(done)
    totalsec = Graphics.frame_count / Graphics.frame_rate
    hour = totalsec / 60 / 60
    min = totalsec / 60 % 60
    time = hour > 0 ? _INTL("{1}h {2}m", hour, min) : _INTL("{1}m", min)
    start = $PokemonGlobal.startTime
    started = start ? _INTL("{1} {2}, {3}", pbGetAbbrevMonthName(start.mon), start.day, start.year) : ""
    { done: done, place: $game_map.name.to_s, name: $Trainer.name.to_s, male: !$Trainer.female?,
      id: "#{_INTL('ID No.')} #{format('%05d', $Trainer.public_ID)}",
      rows: [[ui.l(:badges), $Trainer.badge_count.to_s],
             [ui.l(:pokedex), format("%d/%d", $Trainer.pokedex.owned_count, $Trainer.pokedex.seen_count)],
             [ui.l(:time), time], [ui.l(:money), _INTL("${1}", $Trainer.money.to_s_formatted)], [ui.l(:started), started]],
      party: $Trainer.party.first(6).map { |p| species_icon(p) }, char: ui.file("Graphics/Characters/#{$game_player.character_name}"),
      save_label: _INTL("Save") }
  end

  def self.modal_save
    pbPlayDecisionSE
    $game_temp.in_menu = true
    done = false
    modal(:save) do |cmd|
      @modal_view = -> { screen(:save, [done, Graphics.frame_count / (Graphics.frame_rate * 60)]) { ui.save(save_data(done)) } }
      next :done if cmd == "back"
      if !done && (cmd == "save" || Input.trigger?(Input::USE))
        pbSEPlay("GUI save choice") rescue nil
        if Game.save
          done = true
          @key = nil
        else
          pbPlayBuzzerSE
        end
      elsif done && Input.trigger?(Input::USE)
        next :done
      end
    end
    pbPlayCloseMenuSE unless @modal_exit
  ensure
    $game_temp.in_menu = false
  end

  # ---- hooks ----

  # Runs after a method of one of the game's classes. An alias chain, not a prepended module: the game's own
  # scripts (and plugins) wrap methods with "alias old update" later on, and an alias taken after a prepend
  # points back at the prepended method, which recurses forever.
  def self.after(klass, meth, tag, &block)
    als = :"ashdroid_ds_#{tag}"
    return unless klass.method_defined?(meth) || klass.private_method_defined?(meth)
    return if klass.method_defined?(als) || klass.private_method_defined?(als)
    klass.class_eval do
      alias_method als, meth
      define_method(meth) do |*args, &blk|
        begin
          send(als, *args, &blk)
        ensure
          begin
            block.call(self)
          rescue StandardError, NameError
          end
        end
      end
    end
    AshDroidLog.log("ds: hooked #{klass.name}##{meth}")
  rescue StandardError, NameError => e
    AshDroidLog.log("ds: hook #{klass} #{meth} #{e.class}: #{e.message}")
  end

  def self.install_core
    return if @core || !available?
    @core = true
    graphics = Graphics.singleton_class
    unless graphics.method_defined?(:ashdroid_ds_update)
      graphics.class_eval do
        alias_method :ashdroid_ds_update, :update
        def update(*args)
          AshDroidDS.tick
          ashdroid_ds_update(*args)
        end
      end
    end
    input = Input.singleton_class
    unless input.method_defined?(:ashdroid_ds_trigger?)
      input.class_eval do
        alias_method :ashdroid_ds_trigger?, :trigger?
        def trigger?(button)
          return true if AshDroidDS.forcing? && AshDroidDS.forced?(button)
          ashdroid_ds_trigger?(button)
        end
      end
    end
    AshDroidLog.log("ds: core hooked")
  rescue StandardError, NameError => e
    AshDroidLog.log("ds: install_core #{e.class}: #{e.message}")
  end

  def self.install_world(scene_class)
    return unless available?
    install_core
    after(scene_class, :update, "update") { |scene| AshDroidDS.world_frame(scene) }
    return unless scene_class.method_defined?(:call_menu) && !scene_class.method_defined?(:ashdroid_ds_call_menu)
    scene_class.class_eval do
      alias_method :ashdroid_ds_call_menu, :call_menu
      def call_menu(*args)
        if AshDroidDS.take_menu?
          AshDroidDS.home_cursor
          return unless $game_temp.menu_calling
        end
        ashdroid_ds_call_menu(*args)
      ensure
        AshDroidDS.instance_variable_set(:@want_game_menu, false)
      end
    end
  rescue StandardError, NameError => e
    AshDroidLog.log("ds: install_world #{e.class}: #{e.message}")
  end

  def self.install_menu(klass)
    return unless available?
    install_core
    case klass.name
    when "PokemonParty_Scene" then after(klass, :update, "update") { |s| AshDroidDS.mark(:party, s) }
    when "PokemonBag_Scene" then after(klass, :pbUpdate, "pbUpdate") { |s| AshDroidDS.mark(:bag, s) }
    when "PokemonSummary_Scene" then after(klass, :pbUpdate, "pbUpdate") { |s| AshDroidDS.mark(:summary, s) }
    when "Window_CommandPokemon", "Window_AdvancedCommandPokemon"
      after(klass, :update, "update_#{klass.name.downcase}") { |w| AshDroidDS.mark(:cmd, w) if w.visible && (!w.respond_to?(:active) || w.active) }
    when "PokemonPartyScreen"
      return unless klass.method_defined?(:pbPokemonScreen) && !klass.method_defined?(:ashdroid_ds_pbPokemonScreen)
      klass.class_eval do
        alias_method :ashdroid_ds_pbPokemonScreen, :pbPokemonScreen
        def pbPokemonScreen(*args)
          AshDroidDS.instance_variable_set(:@party_free, true)
          ashdroid_ds_pbPokemonScreen(*args)
        ensure
          AshDroidDS.instance_variable_set(:@party_free, false)
        end
      end
    end
  rescue StandardError, NameError => e
    AshDroidLog.log("ds: install_menu #{e.class}: #{e.message}")
  end

  def self.install(scene_class)
    return unless available?
    install_core
    scene_class.prepend(Scene) unless scene_class.ancestors.include?(Scene)
    AshDroidLog.log("ds: battle menus hooked (#{scene_class.name})")
  rescue StandardError, NameError => e
    AshDroidLog.log("ds: install #{e.class}: #{e.message}")
  end
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
  # Naming screen: one 24-pixel slot per letter from x=160 runs off the box (and the screen)
  # with the game's 16-letter nicknames. Narrow the slots so they all fit in the box.
  module NameEntryFit
    NAME_AREA = 322

    def ashdroid_step
      n = @maxlength.to_i
      n > 0 && n * 24 > NAME_AREA ? NAME_AREA / n : 24
    end

    def pbStartScene(*args)
      r = super
      ashdroid_move_gender
      st = ashdroid_step
      if st != 24
        line = Bitmap.new(st, 6)
        # The game's slot is a 22-pixel line plus its shadow in 24: keep a visible gap
        line.fill_rect(2, 2, st - 6, 4, Color.new(168, 184, 184))
        line.fill_rect(0, 0, st - 6, 4, Color.new(16, 24, 32))
        @ashdroid_line = line
        @maxlength.times do |i|
          s = @sprites["blank#{i}"]
          next unless s
          s.x = 160 + st * i
          s.bitmap = line
        end
        @refreshOverlay = true
        pbDoUpdateOverlay
      end
      r
    end

    # The ♂/♀ of a Pokémon sits at x=430, where the game's default 10 slots end; with 12 slots it
    # lands on the last one. Put it after the question instead ("¿Mote para Doduo? ♀").
    def ashdroid_move_gender
      g = @sprites && @sprites["gender"]
      return unless g && 160 + 24 * @maxlength.to_i > g.x
      b = @sprites["bgoverlay"].bitmap
      g.x = [160 + b.text_size(@helptext.to_s).width + 10, 480].min
      g.y = 12
    rescue StandardError
    end

    def pbDoUpdateOverlay
      st = ashdroid_step
      return super if st == 24
      return unless @refreshOverlay
      @refreshOverlay = false
      bgoverlay = @sprites["bgoverlay"].bitmap
      bgoverlay.clear
      pbSetSystemFont(bgoverlay)
      base = Color.new(16, 24, 32)
      shadow = Color.new(168, 184, 184)
      tp = [[@helptext, 160, 6, false, base, shadow]]
      @helper.textChars.each_with_index do |ch, i|
        w = bgoverlay.text_size(ch).width
        tp.push([ch, 160 + st * i + [(st - 6 - w) / 2, 0].max, 42, false, base, shadow])
      end
      pbDrawTextPositions(bgoverlay, tp)
    end

    def pbEndScene(*args)
      r = super
      @ashdroid_line.dispose if @ashdroid_line && !@ashdroid_line.disposed?
      r
    end
  end

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
    add_spain(settings)
    add_italian(settings)
  end

  # "Italiano" at the end of the list (so no existing language changes index), loading the
  # game's English file; only when the Italian pack is there.
  def self.add_italian(settings)
    langs = settings::LANGUAGES
    return if langs.any? { |l| l[0] == AshDroidText::ITALIAN_NAME }
    return unless File.exist?("ashdroid_it.dat")
    en = langs.find { |l| Array(l).join(" ") =~ /english/i } || langs[0]
    return unless en
    entry = [AshDroidText::ITALIAN_NAME, en[1]]
    list = langs.map { |l| l.frozen? ? l.dup : l } + [entry]
    settings.send(:remove_const, :LANGUAGES)
    settings.const_set(:LANGUAGES, langs.frozen? ? list.freeze : list)
    AshDroidLog.log("language added: #{entry.inspect} at #{list.size - 1}")
  rescue StandardError => e
    AshDroidLog.log("add_italian error: #{e.class}: #{e.message}")
  end

  def self.add_spain(settings)
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

  # Accented capitals (É, Ã, Ú…) rise above the font's ascent. Engines that size the text surface
  # to its contents make it taller and centre it, so a line holding one sinks a little (and a
  # lone "Ã" on the naming keyboard, or "É" in a message drawn letter by letter, sits lower than
  # its neighbours). Measured per font and per letter, from the rendered pixels: "E" + letter is
  # drawn and the bottom of its "E" compared with an "E" on its own. 0 where the engine doesn't
  # move anything. A line gets the largest shift of the capitals it holds.
  def self.tall_shift(bitmap, text)
    caps = text.each_char.select { |c| c.ord > 127 && c =~ /\p{Upper}/ }.uniq
    return 0 if caps.empty?
    caps.map { |c| tall_char(bitmap.font, c) }.max
  rescue StandardError
    0
  end

  def self.tall_char(f, ch)
    key = [f.name.to_s, f.size, f.bold, ch]
    @tall ||= {}
    return @tall[key] if @tall.key?(key)
    @tall[key] = 0
    h = f.size.to_i + 3
    w = f.size.to_i * 3
    ew = nil
    bottoms = ["E", "E" + ch].map do |s|
      b = Bitmap.new(w, h)
      b.font.name = f.name
      b.font.size = f.size
      b.font.bold = f.bold
      b.font.color = Color.new(255, 255, 255)
      ew ||= b.__ashdroid_text_size("E").width
      b.__ashdroid_draw_text(0, 0, w, h, s)
      y = (h - 1).downto(0).find { |yy| (0...ew).any? { |x| b.get_pixel(x, yy).alpha > 0 } }
      b.dispose
      y
    end
    d = bottoms.all? ? bottoms[1] - bottoms[0] : 0
    d = 0 if d < 0 || d > h / 3
    AshDroidLog.log("tall text #{key.inspect}: E=#{bottoms[0].inspect} E#{ch}=#{bottoms[1].inspect} shift=#{d}") if d != 0
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
      n = (Settings::LANGUAGES.count { |l| l[0] != AshDroidText::ITALIAN_NAME } rescue nil)
      it = (Settings::LANGUAGES.index { |l| l[0] == AshDroidText::ITALIAN_NAME } rescue nil)
      if v.is_a?(Integer) && n && v >= n && v != it && AshDroidFixes.spain_index
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

  def self.hook_battle_scene(klass)
    return if klass.ancestors.include?(BattleFightAfterSwitch)
    klass.prepend(BattleFightAfterSwitch)
    AshDroidLog.log("fix applied: #{klass.name}")
    AshDroidDS.install(klass)
  rescue StandardError => e
    AshDroidLog.log("battle scene hook error: #{e.class}: #{e.message}")
  end

  HOOKS = {
    "Scene_Map" => ->(m) { AshDroidDS.install_world(m) },
    "PokemonParty_Scene" => ->(m) { AshDroidDS.install_menu(m) },
    "PokemonPartyScreen" => ->(m) { AshDroidDS.install_menu(m) },
    "PokemonBag_Scene" => ->(m) { AshDroidDS.install_menu(m) },
    "PokemonSummary_Scene" => ->(m) { AshDroidDS.install_menu(m) },
    "Window_CommandPokemon" => ->(m) { AshDroidDS.install_menu(m) },
    "Window_AdvancedCommandPokemon" => ->(m) { AshDroidDS.install_menu(m) },
    "MessageTypes" => ->(m) { AshDroidFixes.hook_message_types(m) },
    "AbilitySplashBar" => AbilitySplashBarES,
    "Trainer" => TrainerES,
    "PokemonOption_Scene" => OptionsAshDroid,
    "PokemonSaveScreen" => SaveScreenQuit,
    "Window_AdvancedTextPokemon" => HardcodedTextWindow,
    "PokemonEntryScene2" => NameEntryFit,
    # Nicknames: 12 characters, as in the official games (the game's own setting is 16, more
    # slots than its naming box has room for)
    "Pokemon" => ->(m) {
      if m.const_defined?(:MAX_NAME_SIZE, false) && m.const_get(:MAX_NAME_SIZE) > 12
        m.send(:remove_const, :MAX_NAME_SIZE)
        m.const_set(:MAX_NAME_SIZE, 12)
        AshDroidLog.log("fix applied: Pokemon::MAX_NAME_SIZE = 12")
      end
    },
    "PokeBattle_Scene" => ->(m) { AshDroidFixes.hook_battle_scene(m) }, # Essentials v19
    "Battle::Scene" => ->(m) { AshDroidFixes.hook_battle_scene(m) },    # Essentials v20+
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
  # game's Options ("Intro en español" / "Intro en français" / "Intro (Deutsch)")
  # while the game is in that language.
  LANGS = [[/spanish/i, "es", "Intro en español", ["No", "Sí"]],
           [/french/i, "fr", "Intro en français", ["Non", "Oui"]],
           [/german/i, "de", "Intro (Deutsch)", ["Aus", "An"]]]
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

# Handheld consoles (H700): the console has no stick clicks, so the L2 / R2 triggers do what L3 / R3
# do on the Deck (Input::Y and Input::Z in the game). The triggers are read as axes (some firmware
# reports them as buttons: SDL turns them into axes either way) and given edge detection here.
if ASHDROID_HANDHELD
  module AshDroidHandheld
    TRIGGER = { "Y" => 0, "Z" => 1 } # Input::Y <- left trigger (L2), Input::Z <- right trigger (R2)
    @now = [false, false]
    @prev = [false, false]
    @held = [0, 0]

    def self.sample
      axes = Input::Controller.respond_to?(:axes_trigger) ? Input::Controller.axes_trigger : nil
      @prev = @now
      @now = axes.is_a?(Array) && axes.size >= 2 ? [axes[0].to_f > 0.5, axes[1].to_f > 0.5] : [false, false]
      2.times { |i| @held[i] = @now[i] ? @held[i] + 1 : 0 }
    rescue StandardError
      @now = [false, false]
    end

    def self.index(b)
      return nil unless defined?(Input::Y) && defined?(Input::Z)
      return 0 if b == Input::Y
      return 1 if b == Input::Z
      nil
    end

    def self.press?(b)
      i = index(b)
      i ? @now[i] : false
    end

    def self.trigger?(b)
      i = index(b)
      i ? (@now[i] && !@prev[i]) : false
    end

    def self.repeat?(b)
      i = index(b)
      return false unless i
      @held[i] == 1 || (@held[i] > 20 && (@held[i] - 20) % 6 == 0)
    end
  end

  begin
    module Input
      class << self
        alias_method :__ashdroid_update_hh, :update
        alias_method :__ashdroid_trigger_hh, :trigger?
        alias_method :__ashdroid_press_hh, :press?
        alias_method :__ashdroid_repeat_hh, :repeat?
        def update(*a)
          r = __ashdroid_update_hh(*a)
          AshDroidHandheld.sample
          r
        end
        def trigger?(b)
          __ashdroid_trigger_hh(b) || AshDroidHandheld.trigger?(b)
        end
        def press?(b)
          __ashdroid_press_hh(b) || AshDroidHandheld.press?(b)
        end
        def repeat?(b)
          __ashdroid_repeat_hh(b) || AshDroidHandheld.repeat?(b)
        end
      end
    end
    AshDroidLog.log("handheld: L2/R2 work as L3/R3; layout=#{AshDroidHandheld.swap? ? 'swap' : 'same'}")
  rescue StandardError, NameError => e
    AshDroidLog.log("handheld: trigger hook failed: #{e.class}: #{e.message}")
  end

  # The game's Power Green / Power Clear fonts store their descent as a positive number, which the
  # native Linux engine takes literally (text drawn low, bottoms of p, g, y... cut off). Same fix as
  # the Steam Deck installer (tools/linux/install.py), done here so the console needs no Python:
  # copies with sane metrics are written to Fonts/ under another family name (mkxp.json maps
  # the originals to them with "fontSub"). The game's own files are not touched.
  module AshDroidFonts
    FIXES = [["power green.ttf", "AshDroid Power Green"], ["power clear.ttf", "AshDroid Power Clear"]]

    def self.checksum(b)
      b = b.b + "\0".b * ((-b.bytesize) % 4)
      b.unpack("N*").sum & 0xFFFFFFFF
    end

    def self.fixed_font(src, family)
      data = File.binread(src).b
      n = data[4, 2].unpack1("n")
      recs = {}
      n.times do |i|
        o = 12 + 16 * i
        cs, off, len = data[o + 4, 12].unpack("NNN")
        recs[data[o, 4]] = [o, cs, off, len]
      end
      os2 = recs["OS/2"][2]
      win_asc, win_desc = data[os2 + 74, 4].unpack("nn")
      hhea = recs["hhea"][2]
      metrics = [win_asc, -win_desc, 0].pack("s>s>s>")
      data[hhea + 4, 6] = metrics # ascender, descender, lineGap
      data[os2 + 68, 6] = metrics # sTypoAscender / Descender / LineGap
      names = [[1, family], [2, "Regular"], [4, family], [6, family.tr(" ", "-")]]
      entries = "".b
      strs = "".b
      [[1, 0, 0, "ISO-8859-1"], [3, 1, 0x409, "UTF-16BE"]].each do |pid, eid, lid, enc|
        names.each do |nid, text|
          b = text.encode(enc).b
          entries << [pid, eid, lid, nid, b.bytesize, strs.bytesize].pack("nnnnnn")
          strs << b
        end
      end
      count = entries.bytesize / 12
      table = [0, count, 6 + 12 * count].pack("nnn") + entries + strs
      data << "\0".b * ((-data.bytesize) % 4)
      new_off = data.bytesize
      data << table << "\0".b * ((-table.bytesize) % 4)
      o = recs["name"][0]
      data[o + 4, 12] = [checksum(table), new_off, table.bytesize].pack("NNN")
      ["hhea", "OS/2"].each do |tag|
        o, _, off, ln = recs[tag]
        data[o + 4, 4] = [checksum(data[off, ln])].pack("N")
      end
      data
    end

    def self.install
      dir = "Fonts"
      return false unless File.directory?(dir)
      wrote = false
      FIXES.each do |name, family|
        src = Dir.children(dir).find { |f| f.downcase == name }
        next unless src
        dst = File.join(dir, "ashdroid_" + name.tr(" ", "_"))
        next if File.exist?(dst)
        File.binwrite(dst, fixed_font(File.join(dir, src), family))
        AshDroidLog.log("fonts: wrote #{dst}")
        wrote = true
      end
      wrote
    rescue StandardError => e
      AshDroidLog.log("fonts: #{e.class}: #{e.message}")
      false
    end
  end
  # The engine lists the fonts before this script runs, so copies written now would only be used
  # from the next start: on the first start, ask "Fire Ash.sh" to start the engine again at once.
  if AshDroidFonts.install
    begin
      File.write("ashdroid_restart", "fonts\n")
      AshDroidLog.log("fonts: first start, restarting so the engine loads them")
      exit
    rescue StandardError
    end
  end
end
