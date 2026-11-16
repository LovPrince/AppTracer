package com.princeyy.ghosttracker.forensics

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Data class representing a leftover or active vault directory trace on device storage.
 */
data class VaultFolderTrace(
    val folderName: String,
    val absolutePath: String,
    val fileCount: Int,
    val totalSizeBytes: Long,
    val lastModifiedMs: Long,
    val detectedVaultType: String,
    val isHidden: Boolean
)

/**
 * Deep storage forensic scanner that searches for orphaned vault folders,
 * hidden media directories, and remnants left behind by HideX, AmorK,
 * Calculator Vaults, Gallery Vault, and Private Safe apps.
 *
 * Developed by Princeyy
 */
object VaultFileScanner {

    // Known vault and hider signatures in shared/external storage
    private val VAULT_SIGNATURES = listOf(
        VaultSignature(".hideX", "HideX Calculator Vault", "Encrypted photo/video vault container"),
        VaultSignature(".HideX", "HideX Calculator Vault", "Encrypted photo/video vault container"),
        VaultSignature(".hidex_vault", "HideX Hidden Vault", "HideX hidden vault data"),
        VaultSignature(".amork", "AmorK App Hider", "AmorK clone and hidden files storage"),
        VaultSignature(".amork_hider", "AmorK App Hider", "AmorK hider configuration & assets"),
        VaultSignature(".AmorK", "AmorK App Hider", "AmorK cloned workspace"),
        VaultSignature(".apphider", "App Hider Master", "Hidden app instances & private media"),
        VaultSignature(".app_hider", "App Hider Pro", "Hidden applications sandbox"),
        VaultSignature(".calculator_vault", "Calculator Vault", "Secret calculator photo/video vault"),
        VaultSignature(".calc_vault", "Calculator Vault", "Encrypted vault container"),
        VaultSignature(".privacy_safe", "Privacy Safe / Private Zone", "Hidden personal data safe"),
        VaultSignature(".privacy_box", "Privacy Box", "Encrypted file locker"),
        VaultSignature(".galleryvault", "Gallery Vault", "Encrypted media locker"),
        VaultSignature(".GalleryVault", "Gallery Vault", "Encrypted media locker"),
        VaultSignature(".smartanuj", "Hide It Pro (Audio Manager)", "Disguised Audio Manager vault"),
        VaultSignature(".audio_manager_vault", "Hide It Pro", "Audio Manager hidden container"),
        VaultSignature(".keepsafe", "Keepsafe Private Cloud", "Hidden encrypted media"),
        VaultSignature(".private_space", "Private Space", "Second space/hider container"),
        VaultSignature(".dualspace", "Dual Space Cloner", "Cloned app accounts storage"),
        VaultSignature(".parallel", "Parallel Space", "Cloned app database storage")
    )

    private data class VaultSignature(
        val directoryName: String,
        val appName: String,
        val description: String
    )

    /**
     * Scans storage locations for vault folders and remnants.
     */
    suspend fun scanVaultTraces(context: Context): List<VaultFolderTrace> = withContext(Dispatchers.IO) {
        val traces = mutableListOf<VaultFolderTrace>()

        val candidateRoots = mutableListOf<File>()
        try {
            val extDir = Environment.getExternalStorageDirectory()
            if (extDir != null && extDir.exists()) {
                candidateRoots.add(extDir)
            }
        } catch (e: Throwable) { }

        // Also check primary external files dir parents
        try {
            val appExtDir = context.getExternalFilesDir(null)
            val parent = appExtDir?.parentFile?.parentFile?.parentFile // /storage/emulated/0
            if (parent != null && parent.exists() && parent !in candidateRoots) {
                candidateRoots.add(parent)
            }
        } catch (e: Throwable) { }

        // Standard subdirectories where hiders nest files
        val searchDirs = mutableListOf<File>()
        for (root in candidateRoots) {
            searchDirs.add(root)
            File(root, "Download").let { if (it.exists()) searchDirs.add(it) }
            File(root, "DCIM").let { if (it.exists()) searchDirs.add(it) }
            File(root, "Pictures").let { if (it.exists()) searchDirs.add(it) }
            File(root, "Documents").let { if (it.exists()) searchDirs.add(it) }
            File(root, "Android/data").let { if (it.exists()) searchDirs.add(it) }
        }

        val checkedPaths = mutableSetOf<String>()

        for (dir in searchDirs) {
            try {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (!file.isDirectory) continue
                    val path = file.absolutePath
                    if (checkedPaths.contains(path)) continue
                    checkedPaths.add(path)

                    val fileName = file.name
                    // Match against signature list
                    val matchedSig = VAULT_SIGNATURES.find { sig ->
                        fileName.equals(sig.directoryName, ignoreCase = true) ||
                        fileName.startsWith(sig.directoryName, ignoreCase = true)
                    }

                    if (matchedSig != null) {
                        val (count, size) = inspectFolder(file)
                        traces.add(
                            VaultFolderTrace(
                                folderName = fileName,
                                absolutePath = path,
                                fileCount = count,
                                totalSizeBytes = size,
                                lastModifiedMs = file.lastModified(),
                                detectedVaultType = matchedSig.appName,
                                isHidden = fileName.startsWith(".")
                            )
                        )
                    } else if (fileName.startsWith(".") && isSuspiciousHiddenFolder(fileName)) {
                        val (count, size) = inspectFolder(file)
                        if (count > 0) {
                            traces.add(
                                VaultFolderTrace(
                                    folderName = fileName,
                                    absolutePath = path,
                                    fileCount = count,
                                    totalSizeBytes = size,
                                    lastModifiedMs = file.lastModified(),
                                    detectedVaultType = "Unknown Hidden Vault",
                                    isHidden = true
                                )
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                // Ignore permission or file access errors
            }
        }

        traces.sortedByDescending { it.lastModifiedMs }
    }

    private fun inspectFolder(dir: File): Pair<Int, Long> {
        var count = 0
        var totalSize = 0L
        try {
            val children = dir.listFiles() ?: return Pair(0, 0L)
            for (child in children) {
                if (child.isFile) {
                    count++
                    totalSize += child.length()
                } else if (child.isDirectory && count < 200) {
                    val subChildren = child.listFiles() ?: continue
                    for (sub in subChildren) {
                        if (sub.isFile) {
                            count++
                            totalSize += sub.length()
                        }
                    }
                }
            }
        } catch (e: Throwable) { }
        return Pair(count, totalSize)
    }

    private fun isSuspiciousHiddenFolder(name: String): Boolean {
        val lower = name.lowercase()
        val suspiciousKeywords = listOf("vault", "hide", "secret", "private", "lock", "amork", "calc")
        return suspiciousKeywords.any { lower.contains(it) }
    }
}
