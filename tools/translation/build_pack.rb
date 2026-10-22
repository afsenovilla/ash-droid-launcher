# Builds app/src/main/assets/packs/<id>/ashdroid_<lang>.dat from packs/<id>/<lang>.json
# (es.json: gaps of the game's own Spanish; es_ES.json: full Spain retranslation).
# The .dat is a Marshal'd Hash { normalized English => Spanish } read by
# ashdroid_patches.rb at runtime (Ruby's Marshal needs no extra libraries there).
# Usage: ruby tools/translation/build_pack.rb [pack-id ...]   (default: all packs)
require "json"

def norm(s) = s.gsub(/\s+/, " ").strip

root = File.expand_path("../..", __dir__)
ids = ARGV.empty? ? Dir.children(File.join(root, "packs")).sort : ARGV
ids.each do |id|
  Dir.glob(File.join(root, "packs", id, "*.json")).sort.each do |src|
    lang = File.basename(src, ".json")
    next unless lang =~ /\A[a-z]{2}(_[A-Z]{2})?\z/
    h = {}
    JSON.parse(File.read(src, encoding: "UTF-8")).each do |en, es|
      next if !es.is_a?(String) || es.empty? || es == en
      h[norm(en)] = es
    end
    dst = File.join(root, "app/src/main/assets/packs", id, "ashdroid_#{lang}.dat")
    Dir.mkdir(File.dirname(dst)) rescue nil
    File.binwrite(dst, Marshal.dump(h))
    puts "#{id}/#{lang}: #{h.size} strings -> #{dst.sub(root + '/', '')}"
  end
end
