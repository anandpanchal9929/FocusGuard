package org.focusguard.web

object DnsCodec {
    fun normalize(domain: String): String = java.net.IDN.toASCII(domain.trim().trimEnd('.').lowercase()).also {
        require(it.length in 1..253 && it.split('.').all { label -> label.length in 1..63 && label.first()!='-' && label.last()!='-' && label.all { c -> c.isLetterOrDigit() || c=='-' } }) { "Enter a domain such as example.com, without a URL path" }
    }
    fun matches(domain: String, blocked: Set<String>)=blocked.any { domain==it || domain.endsWith(".$it") }
    fun question(data: ByteArray): String? {
        if(data.size<17 || (data[2].toInt() and 0x80)!=0 || unsigned16(data,4)!=1) return null
        val labels=mutableListOf<String>(); var offset=12
        while(offset<data.size) {
            val len=data[offset++].toInt() and 255
            if(len==0) { if(offset+4>data.size) return null; return try { normalize(labels.joinToString(".")) } catch(e: Exception) { null } }
            if(len>63 || offset+len>data.size || labels.size>127) return null
            labels.add(String(data,offset,len,Charsets.US_ASCII)); offset+=len
        }
        return null
    }
    fun denied(query: ByteArray): ByteArray = query.copyOf().apply {
        this[2]=((query[2].toInt() and 1) or 0x80).toByte(); this[3]=0x83.toByte()
        for(i in 6..11) this[i]=0
    }
    fun unsigned16(bytes: ByteArray,offset: Int)=((bytes[offset].toInt() and 255) shl 8) or (bytes[offset+1].toInt() and 255)
    fun extract(packet: ByteArray): ByteArray? {
        if(packet.size<28 || ((packet[0].toInt() ushr 4) and 15)!=4 || packet[9].toInt()!=17) return null
        val ip=(packet[0].toInt() and 15)*4
        if(ip<20 || ip+8>packet.size || unsigned16(packet,ip+2)!=53 || (unsigned16(packet,6) and 0x3fff)!=0) return null
        val len=unsigned16(packet,ip+4)
        if(len<8 || ip+len>packet.size) return null
        return packet.copyOfRange(ip+8,ip+len)
    }
    fun reply(queryPacket: ByteArray,dns: ByteArray): ByteArray {
        val ip=(queryPacket[0].toInt() and 15)*4
        val out=ByteArray(28+dns.size); out[0]=0x45; put16(out,2,out.size); out[8]=64; out[9]=17
        queryPacket.copyInto(out,12,16,20); queryPacket.copyInto(out,16,12,16)
        put16(out,20,53); put16(out,22,unsigned16(queryPacket,ip)); put16(out,24,8+dns.size)
        dns.copyInto(out,28)
        var sum=0L; for(i in 0 until 20 step 2) sum+=unsigned16(out,i)
        while(sum>65535) sum=(sum and 65535)+(sum ushr 16)
        put16(out,10,sum.toInt().inv() and 65535)
        return out
    }
    private fun put16(bytes: ByteArray,at: Int,value: Int) { bytes[at]=(value ushr 8).toByte(); bytes[at+1]=value.toByte() }
}
