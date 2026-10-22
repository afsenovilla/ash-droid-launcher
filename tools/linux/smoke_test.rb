# CI smoke test for the native Linux engine: runs as the "game" with the PC patch preloaded.
# Writes smoke_test.log; the first line is OK or FAIL.
lines = []
ok = true
check = lambda do |name, cond|
  lines << "#{cond ? 'pass' : 'FAIL'} #{name}"
  ok &&= cond
end
begin
  require "zlib"
  check.call("zlib", Zlib::Deflate.deflate("x").size > 0)
  begin
    require "json"
    check.call("json (stdlib)", JSON.generate([1]) == "[1]")
  rescue LoadError => e
    check.call("json (stdlib): #{e.message}", false)
  end
  check.call("deck detected (SteamDeck=1)", ASHDROID_DECK == "deck")
  check.call("deck keys", ASHDROID_KEYS["C"] == "A" && ASHDROID_KEYS["X"] == "B / START")
  check.call("device mode", ASHDROID_PC == false)
  check.call("F8 text", AshDroidText.fix("F8: Takes a screenshot.") == "To take screenshots, press STEAM + R1.")
  check.call("controller api", defined?(Input::Controller) && Input::Controller.respond_to?(:triggerex?))
  # Live art: Bitmap raw data is RGBA, as ashdroid_art.rb expects
  b = Bitmap.new(2, 1)
  b.set_pixel(0, 0, Color.new(0x11, 0x22, 0x33, 0x44))
  check.call("raw_data RGBA", b.raw_data.unpack("N*")[0] == 0x11223344)
  b.raw_data = [0xAABBCCFF, 0x01020304].pack("N*")
  c = b.get_pixel(0, 0)
  check.call("raw_data= RGBA", [c.red, c.green, c.blue, c.alpha].map(&:to_i) == [0xAA, 0xBB, 0xCC, 0xFF])
  check.call("live art enabled", AshDroidGfx::Live.enabled?)
  img = AshDroidArt::Img.new(16, 8, Array.new(128, 0xE0E0E0FF))
  check.call("controls art", AshDroidArt::Controls.generate("help_arrows", img, {}, nil).is_a?(AshDroidArt::Img))
  check.call("START hook", Input.respond_to?(:__ashdroid_trigger) && Input.trigger?(Input::B) == false)
  lines << "ruby #{RUBY_VERSION} platform #{(System.platform rescue '?')}"
rescue Exception => e
  ok = false
  lines << "FAIL exception #{e.class}: #{e.message}"
  lines.concat(e.backtrace.to_a.first(5))
end
File.write("smoke_test.log", ([ok ? "OK" : "FAIL"] + lines).join("\n") + "\n")
exit
