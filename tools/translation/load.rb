class OrderedHash < Hash
  def self._load(string)
    ret = self.new
    kv = Marshal.load(string)
    kv[0].each_with_index { |k, i| ret[k] = kv[1][i] }
    ret
  end
end
def ld(f) = File.open(f,"rb"){|io| Marshal.load(io)}
D = File.join(ENV.fetch("GAME_DIR") { abort "Set GAME_DIR to the game folder" }, "Data", "")
def u(s) = s.dup.force_encoding("UTF-8")
