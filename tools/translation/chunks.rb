require "./load"; require "json"
base = ld(D+"messages.dat"); es = ld(D+"spanish.dat")
real = ->(s){ s.gsub(/\\[a-zA-Z]+(\[[^\]]*\])?|\{\d+\}|<[^>]+>/,"").scan(/[A-Za-z]{2,}/).size >= 1 }
other = []
[15,16,17,19,20,22,23].each do |t|
  base[t].each_key do |k|
    k = u(k); next unless real.(k)
    v = es[t][k]; v = u(v) if v
    next if v && v != k
    next if v == k && k.split.size < 2
    other << k
  end
end
ui = (JSON.parse(File.read("ui_missing2.json")) + other).uniq
map = JSON.parse(File.read("map_missing.json")) - ui
Dir.mkdir("chunks") rescue nil
Dir.glob("chunks/*").each { |f| File.delete(f) }
n = 0
[["ui", ui, 220], ["map", map, 230]].each do |kind, list, size|
  list.each_slice(size).with_index do |sl, i|
    File.write(format("chunks/%s_%02d.json", kind, i), JSON.pretty_generate(sl)); n += 1
  end
end
puts "ui=#{ui.size} map=#{map.size} chunks=#{n}"
