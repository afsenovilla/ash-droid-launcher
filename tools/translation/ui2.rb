require "./load"; require "json"
N = ->(s){ s.gsub(/\s+/, " ").strip }
es = ld(D+"spanish.dat")
have = {}
es[24].each { |k, v| have[N.(u(k))] = u(v) if v && v != k }
[15,16,17,19,20,22,23].each { |t| es[t].each { |k, v| have[N.(u(k))] = u(v) if v && v != k } }
strs = JSON.parse(File.read("script_strings.json"))
real = ->(s){ s.gsub(/\\[a-zA-Z]+(\[[^\]]*\])?|\{\d+\}|<[^>]+>/,"").scan(/[A-Za-z]{2,}/).size >= 1 }
missing = []; found = 0; exact = 0
existing_hits = {}
strs.each do |s|
  next unless real.(s)
  if (v = have[N.(s)])
    found += 1
    existing_hits[N.(s)] = v
  else
    missing << s
  end
end
# also map-related: keep previously computed UI from other types
File.write("ui_missing2.json", JSON.pretty_generate(missing))
File.write("existing_fuzzy.json", JSON.pretty_generate(existing_hits))
puts "script strings with letters: #{found + missing.size}; already translated (normalized match): #{found}; missing: #{missing.size} words=#{missing.sum{|x| x.split.size}}"
