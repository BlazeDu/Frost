package com.storm.coldwind

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SecondActivity : AppCompatActivity() {
    init {
        System.loadLibrary("coldwind")
    }

    // ==================== Dword (4字节整数) ====================
    fun writeDword(pid: Int, address: Long, value: Int): Boolean {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        return writeRaw(pid, address, bytes)
    }

    fun readDword(pid: Int, address: Long): Int? {
        val bytes = readRaw(pid, address, 4)
        return bytes?.let {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).int
        }
    }

    // ==================== Float (4字节浮点数) ====================
    fun writeFloat(pid: Int, address: Long, value: Float): Boolean {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()
        return writeRaw(pid, address, bytes)
    }

    fun readFloat(pid: Int, address: Long): Float? {
        val bytes = readRaw(pid, address, 4)
        return bytes?.let {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).float
        }
    }

    // ==================== Double (8字节双精度浮点数) ====================
    fun writeDouble(pid: Int, address: Long, value: Double): Boolean {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array()
        return writeRaw(pid, address, bytes)
    }

    fun readDouble(pid: Int, address: Long): Double? {
        val bytes = readRaw(pid, address, 8)
        return bytes?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).double }
    }

    // ==================== Word (2字节整数) ====================
    fun writeWord(pid: Int, address: Long, value: Short): Boolean {
        val bytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
        return writeRaw(pid, address, bytes)
    }

    fun readWord(pid: Int, address: Long): Short? {
        val bytes = readRaw(pid, address, 2)
        return bytes?.let {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).short
        }
    }

    // ==================== Byte (1字节) ====================
    fun writeByte(pid: Int, address: Long, value: Byte): Boolean {
        return writeRaw(pid, address, byteArrayOf(value))
    }

    fun readByte(pid: Int, address: Long): Byte? {
        val bytes = readRaw(pid, address, 1)
        return bytes?.firstOrNull()
    }

    // ==================== Qword (8字节整数) ====================
    fun writeQword(pid: Int, address: Long, value: Long): Boolean {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        return writeRaw(pid, address, bytes)
    }

    fun readQword(pid: Int, address: Long): Long? {
        val bytes = readRaw(pid, address, 8)
        return bytes?.let {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).long
        }
    }

    // ==================== 底层读写 ====================
    private fun writeRaw(pid: Int, address: Long, data: ByteArray): Boolean {
        return try {
            val hexBytes = data.joinToString("") { "\\x%02x".format(it) }
            val cmd = "su -c \"printf '$hexBytes' | dd of=/proc/$pid/mem bs=1 count=${data.size} seek=$address conv=notrunc 2>/dev/null\""
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun readRaw(pid: Int, address: Long, size: Int): ByteArray? {
        return try {
            val cmd = "su -c dd if=/proc/$pid/mem bs=1 skip=$address count=$size 2>/dev/null"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val result = process.inputStream.readBytes()
            process.waitFor()
            if (result.size == size) result else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    external fun getModuleBase(pid: Int, moduleName: String): Long

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
        val btnClick = findViewById<Button>(R.id.function1)
        btnClick.setOnClickListener {
            val targetPkg = "com.minitech.miniworld.TMobile.mi"
            val pid = getPidByPackage("com.MA.Polyfield")
            val baseLibIl2cpp = getModuleBase(pid, "libil2cpp.so")
            val p1 = readQword(pid,baseLibIl2cpp+0xC880)
            Log.i("内存修改","地址:"+p1)
        }
    }

    private fun getPidByPackage(packageName: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A | grep $packageName"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()
            if (line != null) {
                val parts = line.trim().split(Regex("\\s+"))
                parts[1].toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }
}