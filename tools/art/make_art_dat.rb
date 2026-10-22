#!/usr/bin/env ruby
# Packs the art recipes and pixel fonts for the PC / Steam Deck patch (read by ashdroid_art.rb
# with Marshal, so the game's Ruby needs no json library):
#   ruby tools/art/make_art_dat.rb app/src/main/assets/packs/fire-ash/art.json OUT.dat
require "json"
root = File.expand_path("../..", __dir__)
recipes = JSON.parse(File.read(ARGV[0], encoding: "UTF-8"))
fonts = {}
cur = ch = nil
File.foreach(File.join(root, "app/src/main/assets/pixelfonts.txt"), encoding: "UTF-8") do |line|
  line = line.chomp
  next if line.start_with?("//") || line.empty?
  if line.start_with?("@font")
    _, name, w, h = line.split
    cur = fonts[name] = { "w" => w.to_i, "h" => h.to_i, "g" => {} }
  elsif line.start_with?("char:")
    ch = line[5..]
    cur["g"][ch] = []
  else
    cur["g"][ch] << line
  end
end
File.binwrite(ARGV[1], Marshal.dump({ "recipes" => recipes, "fonts" => fonts }))
puts "#{ARGV[1]}: #{recipes['images'].size} recipes, #{fonts.size} fonts"
