package dev.shallowdusty.oplusotastudio.download

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferences

class DownloadWorkRequestFactory {

    fun create(
        taskId: String,
        preferences: DownloadPreferences,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (preferences.wifiOnly) {
                            NetworkType.UNMETERED
                        } else {
                            NetworkType.CONNECTED
                        },
                    )
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInputData(workDataOf(DownloadWorker.TaskIdKey to taskId))
            .addTag(DownloadWorker.WorkTag)
            .build()
}
