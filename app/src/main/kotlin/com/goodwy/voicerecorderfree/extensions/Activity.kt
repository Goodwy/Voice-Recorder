package com.goodwy.voicerecorderfree.extensions

import android.app.Activity
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.core.net.toUri
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.FilePickerDialog
import com.goodwy.commons.extensions.createDocumentUriUsingFirstParentTreeUri
import com.goodwy.commons.extensions.createSAFDirectorySdk30
import com.goodwy.commons.extensions.deleteFile
import com.goodwy.commons.extensions.getDoesFilePathExistSdk30
import com.goodwy.commons.extensions.hasProperStoredFirstParentUri
import com.goodwy.commons.extensions.isPlayStoreInstalled
import com.goodwy.commons.extensions.isRuStoreInstalled
import com.goodwy.commons.extensions.toFileDirItem
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.FileDirItem
import com.goodwy.voicerecorderfree.R
import com.goodwy.voicerecorderfree.BuildConfig
import com.goodwy.voicerecorderfree.activities.SimpleActivity
import com.goodwy.voicerecorderfree.dialogs.StoragePermissionDialog
import com.goodwy.voicerecorderfree.models.Recording
import java.io.File

fun Activity.setKeepScreenAwake(keepScreenOn: Boolean) {
    if (keepScreenOn) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

fun BaseSimpleActivity.ensureStoragePermission(callback: (result: Boolean) -> Unit) {
    if (isRPlus() && !hasProperStoredFirstParentUri(config.saveRecordingsFolder)) {
        StoragePermissionDialog(this) {
            launchFolderPicker(config.saveRecordingsFolder) { newPath ->
                if (!newPath.isNullOrEmpty()) {
                    config.saveRecordingsFolder = newPath
                    callback(true)
                } else {
                    callback(false)
                }
            }
        }
    } else {
        callback(true)
    }
}

fun BaseSimpleActivity.launchFolderPicker(
    currentPath: String,
    callback: (newPath: String?) -> Unit
) {
    FilePickerDialog(
        activity = this,
        currPath = currentPath,
        pickFile = false,
        showFAB = true,
        showRationale = false
    ) { path ->
        handleSAFDialog(path) { grantedSAF ->
            if (!grantedSAF) {
                callback(null)
                return@handleSAFDialog
            }

            handleSAFDialogSdk30(path, showRationale = false) { grantedSAF30 ->
                if (!grantedSAF30) {
                    callback(null)
                    return@handleSAFDialogSdk30
                }

                callback(path)
            }
        }
    }
}

fun BaseSimpleActivity.deleteRecordings(
    recordingsToRemove: Collection<Recording>,
    callback: (success: Boolean) -> Unit
) {
    ensureBackgroundThread {
        if (isRPlus()) {
            val resolver = contentResolver
            recordingsToRemove.forEach {
                DocumentsContract.deleteDocument(resolver, it.path.toUri())
            }
        } else {
            recordingsToRemove.forEach {
                val fileDirItem = File(it.path).toFileDirItem(this)
                deleteFile(fileDirItem)
            }
        }

        callback(true)
    }
}

fun BaseSimpleActivity.trashRecordings(
    recordingsToMove: Collection<Recording>,
    callback: (success: Boolean) -> Unit
) = moveRecordings(
    recordingsToMove = recordingsToMove,
    sourceParent = config.saveRecordingsFolder,
    destinationParent = getOrCreateTrashFolder(),
    callback = callback
)

fun BaseSimpleActivity.restoreRecordings(
    recordingsToRestore: Collection<Recording>,
    callback: (success: Boolean) -> Unit
) = moveRecordings(
    recordingsToMove = recordingsToRestore,
    sourceParent = getOrCreateTrashFolder(),
    destinationParent = config.saveRecordingsFolder,
    callback = callback
)

fun BaseSimpleActivity.moveRecordings(
    recordingsToMove: Collection<Recording>,
    sourceParent: String,
    destinationParent: String,
    callback: (success: Boolean) -> Unit
) {
    if (isRPlus()) {
        moveRecordingsSAF(
            recordings = recordingsToMove,
            sourceParent = sourceParent,
            destinationParent = destinationParent,
            callback = callback
        )
    } else {
        moveRecordingsLegacy(
            recordings = recordingsToMove,
            sourceParent = sourceParent,
            destinationParent = destinationParent,
            callback = callback
        )
    }
}

private fun BaseSimpleActivity.moveRecordingsSAF(
    recordings: Collection<Recording>,
    sourceParent: String,
    destinationParent: String,
    callback: (success: Boolean) -> Unit
) {
    ensureBackgroundThread {
        val contentResolver = contentResolver
        val sourceParentDocumentUri = createDocumentUriUsingFirstParentTreeUri(sourceParent)
        val destinationParentDocumentUri =
            createDocumentUriUsingFirstParentTreeUri(destinationParent)

        if (!getDoesFilePathExistSdk30(destinationParent)) {
            createSAFDirectorySdk30(destinationParent)
        }

        recordings.forEach { recording ->
            try {
                DocumentsContract.moveDocument(
                    contentResolver,
                    recording.path.toUri(),
                    sourceParentDocumentUri,
                    destinationParentDocumentUri
                )
            } catch (@Suppress("SwallowedException") e: IllegalStateException) {
                val sourceUri = recording.path.toUri()
                contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    val targetPath = File(destinationParent, recording.title).absolutePath
                    val targetUri = createDocumentFile(targetPath) ?: return@forEach
                    contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    DocumentsContract.deleteDocument(contentResolver, sourceUri)
                }
            }
        }

        callback(true)
    }
}

private fun BaseSimpleActivity.moveRecordingsLegacy(
    recordings: Collection<Recording>,
    sourceParent: String,
    destinationParent: String,
    callback: (success: Boolean) -> Unit
) {
    copyMoveFilesTo(
        fileDirItems = recordings
            .map { File(it.path).toFileDirItem(this) }
            .toMutableList() as ArrayList<FileDirItem>,
        source = sourceParent,
        destination = destinationParent,
        isCopyOperation = false,
        copyPhotoVideoOnly = false,
        copyHidden = false
    ) {
        callback(true)
    }
}

fun BaseSimpleActivity.deleteTrashedRecordings() {
    deleteRecordings(getAllRecordings(trashed = true)) {}
}

fun BaseSimpleActivity.deleteExpiredTrashedRecordings() {
    if (
        config.useRecycleBin &&
        config.lastRecycleBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000
    ) {
        config.lastRecycleBinCheck = System.currentTimeMillis()
        ensureBackgroundThread {
            try {
                val recordingsToRemove = getAllRecordings(trashed = true)
                    .filter { it.timestamp < System.currentTimeMillis() - MONTH_SECONDS * 1000L }
                if (recordingsToRemove.isNotEmpty()) {
                    deleteRecordings(recordingsToRemove) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun SimpleActivity.launchPurchase() {
    val productIdX1 = BuildConfig.PRODUCT_ID_X1
    val productIdX2 = BuildConfig.PRODUCT_ID_X2
    val productIdX3 = BuildConfig.PRODUCT_ID_X3
    val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    startPurchaseActivity(
        R.string.app_name_g,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        playStoreInstalled = isPlayStoreInstalled(),
        ruStoreInstalled = isRuStoreInstalled()
    )
}

fun SimpleActivity.launchAbout() {
    val licenses = LICENSE_EVENT_BUS or LICENSE_AUDIO_RECORD_VIEW or LICENSE_ANDROID_LAME or LICENSE_AUTOFITTEXTVIEW

    val faqItems = arrayListOf(
        FAQItem(R.string.faq_1_title, R.string.faq_1_text),
        FAQItem(com.goodwy.commons.R.string.faq_2_title_commons, com.goodwy.strings.R.string.faq_2_text_commons_g),
        FAQItem(com.goodwy.commons.R.string.faq_6_title_commons, com.goodwy.strings.R.string.faq_6_text_commons_g),
        FAQItem(com.goodwy.commons.R.string.faq_9_title_commons, com.goodwy.commons.R.string.faq_9_text_commons)
    )

    val productIdX1 = BuildConfig.PRODUCT_ID_X1
    val productIdX2 = BuildConfig.PRODUCT_ID_X2
    val productIdX3 = BuildConfig.PRODUCT_ID_X3
    val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    startAboutActivity(
        appNameId = R.string.app_name_g,
        licenseMask = licenses,
        versionName = BuildConfig.VERSION_NAME,
        faqItems = faqItems,
        showFAQBeforeMail = true,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        playStoreInstalled = isPlayStoreInstalled(),
        ruStoreInstalled = isRuStoreInstalled()
    )
}
