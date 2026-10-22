# Ash Droid Launcher - runtime text patches (generated, loaded via mkxp.json "preloadScript").
# Nothing in the game files is modified: this only rewrites some on-screen texts
# so they talk about the device's buttons instead of keyboard keys.
#
# The launcher replaces the line below with the player's current button mapping:
# keyboard key the game mentions => button name on this device.
ASHDROID_KEYS = {} # __ASHDROID_KEYS__
# System language of the device ("es", "en"…), used when the game's own language can't be read.
ASHDROID_LANG = "en" # __ASHDROID_LANG__

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
AshDroidLog.log("patch loaded; cwd=#{Dir.pwd rescue '?'} ruby=#{RUBY_VERSION}")

module AshDroidText
  BUTTON_WORD = { "Default" => "Button", "default" => "button",
                  "Predeterminada" => "Botón", "Predeterminado" => "Botón",
                  "predeterminada" => "botón", "predeterminado" => "botón",
                  "Por defecto" => "Botón", "por defecto" => "botón" }

  # Controls-screen texts the game's Spanish translation leaves in English.
  # Only applied when the game is set to Spanish. The "(Default: X)" part is kept
  # and then turned into the device's button by FIXES below.
  ES = [
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

  def self.norm(s)
    s.gsub(/\s+/, " ").strip
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
    # Arrow keys -> D-pad
    [/\bthe Arrow keys\b/i, "the D-pad"],
    [/\bArrow keys\b/i, "D-pad"],
    [/\blas teclas de dirección\b/i, "la cruceta"],
    [/\bteclas de dirección\b/i, "cruceta"],
    [/\bpuedes usarlas\b/i, "puedes usarla"],
    # F1 (mkxp key bindings window) doesn't exist on the device
    [/\A.*(Key Bindings window|ventana de asignación de teclas).*\z/m, lambda { |m|
      m[0] =~ /asignaci/ ?
        "En esta consola los botones se configuran desde Ash Droid Launcher (\"Configurar botones\")." :
        "On this device, buttons are configured in Ash Droid Launcher (\"Configure buttons\")."
    }],
    # F8 screenshot: use the device's own screenshot instead
    [/\A.*(captura de pantalla\. Se guarda|Takes? a screenshot).*\z/m, lambda { |m|
      m[0] =~ /captura/ ?
        "Para hacer capturas usa la función de captura de pantalla de la consola." :
        "To take screenshots, use your device's screenshot function."
    }],
  ]

  def self.fix(s)
    return s unless s.is_a?(String)
    out = translate(s)
    FIXES.each do |re, rep|
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
  # the game is in Spanish; "any" ones (button names) always do.
  ES = {
    "graphics/pictures/battle/cursor_command" => "ashdroid_es/battle_cursor_command.png",
  }
  ANY = {}
  LOGGED = {}
  INDEX = "ashdroid_art/index.txt"

  begin
    if File.exist?(INDEX)
      File.read(INDEX).each_line do |line|
        lang, key, file = line.chomp.split("\t")
        next unless file
        (lang == "any" ? ANY : ES)[key] = file
      end
    end
  rescue StandardError
  end

  def self.redirect(path)
    return nil unless path.is_a?(String)
    key = path.tr("\\", "/").sub(/\A\.\//, "").sub(/\.png\z/i, "").downcase
    alt = ANY[key]
    alt ||= ES[key] if AshDroidText.spanish?
    res = alt && File.exist?(alt) ? alt : nil
    if alt && !LOGGED[key]
      LOGGED[key] = true
      AshDroidLog.log("gfx #{key} -> #{res.inspect}")
    end
    res
  rescue StandardError
    nil
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
      return unless self == Object && ASHDROID_WRAP.include?(name)
      return if @__ashdroid_wrapping
      AshDroidLog.log("wrapping #{name}")
      @__ashdroid_wrapping = true
      begin
        orig = instance_method(name)
        key_index = name.to_s.start_with?("_MAP") ? 1 : 0
        define_method(name) do |*args, &blk|
          if (es = AshDroidText.lookup(args[key_index]))
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
