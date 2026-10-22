require "./load"; require "zlib"; require "ripper"; require "json"
codes = []
sc = ld(D+"Scripts.rxdata")
sc.each { |s| codes << [u(s[1]), Zlib::Inflate.inflate(s[2]).force_encoding("UTF-8")] rescue nil }
ps = ld(D+"PluginScripts.rxdata") rescue nil
puts "plugin structure: #{ps.class} #{ps.length rescue nil} #{ps.first.class rescue nil}"
if ps
  ps.each do |plugin|
    # [name, meta, [[filename, compressed_code], ...]]
    files = plugin[2] rescue nil
    (files || []).each { |f| codes << [u(f[0]), Zlib::Inflate.inflate(f[1]).force_encoding("UTF-8")] rescue nil }
  end
end
puts "script sections: #{codes.size}"
# Find string literal first args of _INTL/_ISPRINTF/_MAPINTL
strs = {}
codes.each do |name, code|
  toks = Ripper.lex(code) rescue next
  toks.each_with_index do |t, i|
    next unless t[1] == :on_ident && %w[_INTL _ISPRINTF _MAPINTL _MAPISPRINTF].include?(t[2])
    j = i + 1
    j += 1 while toks[j] && [:on_sp, :on_lparen].include?(toks[j][1])
    j += 1 if t[2].start_with?("_MAP") && toks[j] # skip map id arg roughly
    if t[2].start_with?("_MAP")
      j += 1 while toks[j] && ![:on_comma].include?(toks[j][1]); j += 1
      j += 1 while toks[j] && toks[j][1] == :on_sp
    end
    next unless toks[j] && [:on_tstring_beg].include?(toks[j][1])
    beg = toks[j][2]; parts = []; k = j + 1
    ok = true
    while toks[k] && toks[k][1] != :on_tstring_end
      case toks[k][1]
      when :on_tstring_content then parts << toks[k][2]
      else ok = false; break
      end
      k += 1
    end
    next unless ok
    raw = parts.join
    val = beg == "'" ? raw.gsub("\\\\", "\\").gsub("\\'", "'") : (eval("\"#{raw}\"") rescue nil)
    strs[val] = name if val
  end
end
File.write("script_strings.json", JSON.pretty_generate(strs.keys))
puts "script _INTL strings: #{strs.size}"
