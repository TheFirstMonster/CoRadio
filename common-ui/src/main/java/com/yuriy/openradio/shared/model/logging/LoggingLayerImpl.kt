/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.coradio.shared.model.logging

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.coradio.shared.R
import app.coradio.shared.utils.AppUtils
import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LoggingLayerImpl(private val mContext: Context) : LoggingLayer {

    private var mUser = AppUtils.EMPTY_STRING
    private var mPwd = AppUtils.EMPTY_STRING

    override fun collectAdbLogs(onSuccess: (file: File) -> Unit, onError: (msg: String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            clearLogs()
            val logFile = File(getInternalStorageDir(mContext), "$LOGS_FILE_NAME.txt")
            try {
                captureLogcatOutput(logFile)
                onSuccess(zipLogFile(logFile))
            } catch (e: IOException) {
                onError(e.message.toString())
            }
        }
    }

    private fun captureLogcatOutput(logFile: File) {
        val deviceInfo = getDeviceInfo()
        writeDeviceInfoToFile(deviceInfo, logFile, false)

        val applicationBuildInfo = getApplicationBuildInfo(mContext)
        writeDeviceInfoToFile(applicationBuildInfo, logFile, true)

        val processBuilder = ProcessBuilder("logcat", "-d")
        val process = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val writer = BufferedWriter(FileWriter(logFile, true))

        reader.use { readerIt ->
            writer.use { writerIt ->
                var line: String?
                while (readerIt.readLine().also { line = it } != null) {
                    writerIt.write(line)
                    writerIt.newLine()
                }
            }
        }
    }

    override fun sendLogsViaEmail(zipFile: File, onSuccess: () -> Unit, onError: (msg: String) -> Unit) {
        try {
            Transport.send(createEmailMessage(zipFile))
            onSuccess()
        } catch (throwable: Throwable) {
            onError(throwable.message.toString())
        }
    }

    override fun clearLogs() {
        val logFile = File(getInternalStorageDir(mContext), "$LOGS_FILE_NAME.txt")
        val zipFile = File(getInternalStorageDir(mContext), "$LOGS_FILE_NAME.zip")

        // Delete the log file
        if (logFile.exists()) {
            logFile.delete()
        }

        // Delete the zip file
        if (zipFile.exists()) {
            zipFile.delete()
        }
    }

    private fun createEmailMessage(zipFile: File): Message {

        val properties = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
        }

        val session = Session.getInstance(properties, object : Authenticator() {

            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(mUser, mPwd)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(mUser))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(mUser))
            setSubject("ADB logs for CoRadio")

            // Create a multipart message
            val multipart = MimeMultipart()

            // Create a body part for the email text
            val textPart = MimeBodyPart()
            textPart.setText("Attached are the ADB logs from the device.")
            multipart.addBodyPart(textPart)

            // Create a body part for the zip file
            val zipPart = MimeBodyPart()
            val dataSource = FileDataSource(zipFile.absolutePath)
            zipPart.dataHandler = DataHandler(dataSource)
            zipPart.fileName = "adb_logs.zip"
            multipart.addBodyPart(zipPart)

            // Set the multipart as the content of the message
            setContent(multipart)
        }

        return message
    }

    private fun zipLogFile(logFile: File): File {
        val zipFile = File(getInternalStorageDir(mContext), "$LOGS_FILE_NAME.zip")
        zipFile.createNewFile()
        FileOutputStream(zipFile).use { fileOutputStream ->
            ZipOutputStream(BufferedOutputStream(fileOutputStream)).use { zipOutputStream ->
                zipOutputStream.putNextEntry(ZipEntry("$LOGS_FILE_NAME.txt"))
                FileInputStream(logFile).use { fileInputStream ->
                    val buffer = ByteArray(1024)
                    var count: Int
                    while (fileInputStream.read(buffer).also { count = it } != -1) {
                        zipOutputStream.write(buffer, 0, count)
                    }
                }
            }
        }

        return zipFile
    }

    private fun getInternalStorageDir(context: Context): File {
        val directory = File(context.filesDir, LOGS_DIR_NAME)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    private fun writeDeviceInfoToFile(deviceInfo: String, logFile: File, append: Boolean) {
        BufferedWriter(FileWriter(logFile, append)).use { writer ->
            writer.write(deviceInfo)
            writer.newLine()
        }
    }

    private fun getDeviceInfo(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val versionName = Build.VERSION.RELEASE
        val sdkVersion = Build.VERSION.SDK_INT
        val buildType = Build.TYPE
        val buildId = Build.ID
        val buildTime = Build.TIME
        val fingerprint = Build.FINGERPRINT
        val hardware = Build.HARDWARE
        val bootloader = Build.BOOTLOADER
        val board = Build.BOARD
        val radioVersion = Build.getRadioVersion()
        val brand = Build.BRAND
        val cpuAbi = Build.CPU_ABI
        val cpuAbi2 = Build.CPU_ABI2
        val device = Build.DEVICE
        val display = Build.DISPLAY
        val host = Build.HOST
        val tags = Build.TAGS

        return """
        Device Information:
        Manufacturer: $manufacturer
        Model: $model
        Android Version: $versionName (SDK $sdkVersion)
        Build Type: $buildType
        Build ID: $buildId
        Build Time: $buildTime
        Fingerprint: $fingerprint
        Hardware: $hardware
        Bootloader: $bootloader
        Board: $board
        Radio Version: $radioVersion
        Brand: $brand
        CPU ABI: $cpuAbi
        CPU ABI 2: $cpuAbi2
        Device: $device
        Display: $display
        Host: $host
        Tags: $tags
        """.trimIndent()
    }

    private fun getApplicationBuildInfo(context: Context): String {
        val packageManager: PackageManager = context.packageManager
        val packageName: String = context.packageName
        try {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName: String = packageInfo.versionName
            val versionCode: Int = packageInfo.versionCode
            return """
            Application Build Information:
            Package Name: $packageName
            Version Name: $versionName
            Version Code: $versionCode
            """.trimIndent()
        } catch (e: PackageManager.NameNotFoundException) {
            // Ignore
        }
        return "Application Build Information: Not available"
    }

    companion object {

        private const val LOGS_FILE_NAME = "adb_logs"
        private const val LOGS_DIR_NAME = "logs"
    }
}
