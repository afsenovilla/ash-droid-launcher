# Builds app/src/main/assets/packs/<id>/ashdroid_es.dat from packs/<id>/es.json.
# The .dat is a Marshal'd Hash { normalized English => Spanish } read by
# ashdroid_patches.rb at runtime (Ruby's Marshal needs no extra libraries there).
# Usage: ruby tools/translation/build_pack.rb [pack-id ...]   (default: all packs)
require "json"

def norm(s) = s.gsub(/\s+/, " ").strip

root = File.expand_path("../..", __dir__)
ids = ARGV.empty? ? Dir.children(File.join(root, "packs")).sort : ARGV
ids.each do |id|
  src = File.join(root, "packs", id, "es.json")
  next unless File.exist?(src)
  h = {}
  JSON.parse(File.read(src, encoding: "UTF-8")).each do |en, es|
    next if !es.is_a?(String) || es.empty? || es == en
    h[norm(en)] = es
  end
  dst = File.join(root, "app/src/main/assets/packs", id, "ashdroid_es.dat")
  Dir.mkdir(File.dirname(dst)) rescue nil
  File.binwrite(dst, Marshal.dump(h))
  puts "#{id}: #{h.size} strings -> #{dst.sub(root + '/', '')}"
end
