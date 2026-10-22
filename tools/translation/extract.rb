require "./load"; require "json"
base = ld(D+"messages.dat"); es = ld(D+"spanish.dat"); en = ld(D+"english.dat")
def real?(s) = s.is_a?(String) && s.gsub(/\\[a-zA-Z]+(\[[^\]]*\])?/,"").scan(/[A-Za-z]{2,}/).size >= 1
ui = []; map = {}
[15,16,17,19,20,22,23,24].each do |t|
  base[t].each_key do |k|
    next unless real?(k)
    v = es[t][k]
    next if v && v != k
    next if v == k && k.split.size < 2
    ui << u(k)
  end
end
base[0].each_with_index do |mh, mi|
  next unless mh.is_a?(Hash)
  eh = es[0][mi].is_a?(Hash) ? es[0][mi] : {}
  mh.each_key do |k|
    next unless real?(k)
    v = eh[k]
    next if v && v != k
    next if v == k && k.split.size < 3
    map[u(k)] = true
  end
end
ui.uniq!; mk = map.keys - ui
File.write("ui_missing.json", JSON.pretty_generate(ui))
File.write("map_missing.json", JSON.pretty_generate(mk))
g = {}
{5=>"moves",7=>"items",10=>"abilities",12=>"types",13=>"trainer_types",21=>"map_names"}.each do |t,name|
  h = {}; (en[t]||[]).each_with_index{|s,i| v = es[t][i]; h[u(s)] = u(v) if s.is_a?(String) && v.is_a?(String) && !s.empty? && v != s }; g[name] = h
end
h={}; es[19].each{|k,v| h[u(k)] = u(v) if v && v != k}; g["places"] = h
# Sample of the game's own existing Spanish script translations, for style/terminology
s = {}; es[24].each{|k,v| s[u(k)] = u(v) if v && v != k && k.size < 140}; g["existing_ui_translations"] = s
File.write("glossary.json", JSON.pretty_generate(g))
puts "ui=#{ui.size} words=#{ui.sum{|x|x.split.size}}  map=#{mk.size} words=#{mk.sum{|x|x.split.size}}"
