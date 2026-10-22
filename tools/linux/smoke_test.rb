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
  check.call("START hook", Input.respond_to?(:__ashdroid_trigger) && Input.trigger?(Input::B) == false)
  lines << "ruby #{RUBY_VERSION} platform #{(System.platform rescue '?')}"
rescue Exception => e
  ok = false
  lines << "FAIL exception #{e.class}: #{e.message}"
  lines.concat(e.backtrace.to_a.first(5))
end
File.write("smoke_test.log", ([ok ? "OK" : "FAIL"] + lines).join("\n") + "\n")
exit
