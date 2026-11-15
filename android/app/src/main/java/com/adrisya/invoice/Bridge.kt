package com.adrisya.invoice

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class Bridge(private val activity: Activity, private val web: WebView) {

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
    }

    private fun btAdapter(): BluetoothAdapter? {
        val mgr = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        if (Build.VERSION.SDK_INT >= 31) {
            activity.runOnUiThread {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                    1001
                )
            }
        }
    }

    private fun printResult(ok: Boolean, msg: String) {
        val m = JSONObject.quote(msg)
        web.post { web.evaluateJavascript("window.__btPrintResult && __btPrintResult($ok, $m)", null) }
    }

    @JavascriptInterface
    fun listPaired(cbId: String) {
        Thread {
            var json = "[]"
            try {
                if (!hasConnectPermission()) requestPerms()
                val ad = btAdapter()
                if (ad != null && hasConnectPermission()) {
                    val arr = JSONArray()
                    ad.bondedDevices?.forEach { d ->
                        val o = JSONObject()
                        o.put("name", d.name ?: "Printer")
                        o.put("address", d.address)
                        arr.put(o)
                    }
                    json = arr.toString()
                }
            } catch (e: Exception) {
            }
            val quoted = JSONObject.quote(json)
            val idq = JSONObject.quote(cbId)
            web.post { web.evaluateJavascript("window.__btResult && __btResult($idq, $quoted)", null) }
        }.start()
    }

    @JavascriptInterface
    fun printThermal(payload: String) {
        Thread {
            var sock: BluetoothSocket? = null
            try {
                if (!hasConnectPermission()) {
                    requestPerms()
                    throw Exception("Izin Bluetooth belum diberikan - coba lagi")
                }
                val ad = btAdapter() ?: throw Exception("Bluetooth tidak tersedia")
                if (!ad.isEnabled) {
                    activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    throw Exception("Bluetooth mati - aktifkan lalu coba lagi")
                }
                val root = JSONObject(payload)
                val address = root.getString("address")
                val lines = root.getJSONArray("lines")
                val dev = ad.getRemoteDevice(address)
                @Suppress("MissingPermission")
                sock = dev.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                @Suppress("MissingPermission")
                ad.cancelDiscovery()
                sock.connect()
                val out = sock.outputStream
                val buf = ByteArrayOutputStream()
                buf.write(byteArrayOf(0x1B, 0x40)) // ESC @ reset
                buf.write(byteArrayOf(0x1B, 0x61, 0x01)) // tengah
                buf.write(Base64.decode(LogoBytes.B64, Base64.DEFAULT)) // logo raster
                buf.write(byteArrayOf(0x0A))
                buf.write(byteArrayOf(0x1B, 0x61, 0x00)) // kiri
                for (idx in 0 until lines.length()) {
                    val ln = lines.getJSONObject(idx)
                    val bold = ln.optBoolean("b", false)
                    if (bold) buf.write(byteArrayOf(0x1B, 0x45, 0x01))
                    buf.write(ln.getString("t").toByteArray(Charsets.UTF_8))
                    if (bold) buf.write(byteArrayOf(0x1B, 0x45, 0x00))
                    buf.write(byteArrayOf(0x0A))
                }
                buf.write(byteArrayOf(0x1B, 0x64, 0x03)) // umpan 3 baris
                try {
                    buf.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // potong kertas
                } catch (e: Exception) {
                }
                out.write(buf.toByteArray())
                out.flush()
                Thread.sleep(400)
                printResult(true, "")
            } catch (e: Exception) {
                printResult(false, e.message ?: "error")
            } finally {
                try {
                    sock?.close()
                } catch (e: Exception) {
                }
            }
        }.start()
    }

    @JavascriptInterface
    fun sharePdf(b64: String, filename: String, text: String, phone: String) {
        Thread {
            try {
                val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
                val f = File(dir, filename)
                f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", f)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.runOnUiThread {
                    try {
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Pesan Invoice", text))
                        toast("Pesan invoice tersalin - tempel di chat bila teks belum muncul")
                    } catch (e: Exception) { }
                    var terkirim = false
                    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                        try {
                            val wa = Intent(intent)
                            wa.setPackage(pkg)
                            activity.startActivity(wa)
                            terkirim = true
                            break
                        } catch (e: Exception) { }
                    }
                    if (!terkirim) {
                        try { activity.startActivity(Intent.createChooser(intent, "Kirim Invoice")) }
                        catch (e: Exception) { toast("Tidak ada aplikasi untuk mengirim") }
                    }
                }
            } catch (e: Exception) {
                toast("Gagal menyiapkan PDF: " + (e.message ?: ""))
            }
        }.start()
    }

    @JavascriptInterface
    fun shareText(text: String, phone: String) {
        val url = "https://wa.me/" + phone + "?text=" + Uri.encode(text)
        activity.runOnUiThread {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                toast("WhatsApp tidak ditemukan")
            }
        }
    }

    @JavascriptInterface
    fun printHtml(html: String, title: String) {
        activity.runOnUiThread {
            val pv = WebView(activity)
            pv.settings.javaScriptEnabled = false
            pv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String) {
                    val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val attrs = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                    pm.print(title, pv.createPrintDocumentAdapter(title), attrs)
                }
            }
            pv.loadDataWithBaseURL("https://konseptormalam.github.io/", html, "text/html", "UTF-8", null)
        }
    }

    @JavascriptInterface
    fun saveFile(b64: String, filename: String) {
        Thread {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                if (Build.VERSION.SDK_INT >= 29) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                        ?: throw Exception("gagal membuat file")
                    activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                } else {
                    val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
                    File(dir, filename).writeBytes(bytes)
                }
                toast("Backup tersimpan di folder Download")
            } catch (e: Exception) {
                toast("Gagal menyimpan backup")
            }
        }.start()
    }
}
