package cc.kowx712.autohoyolab.worker

import android.util.Log
import cc.kowx712.autohoyolab.auth.HoyoLabApiClient
import cc.kowx712.autohoyolab.data.local.AppDatabase
import cc.kowx712.autohoyolab.data.local.CheckInLog
import cc.kowx712.autohoyolab.data.local.RedeemedCode
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.data.model.HoyoGameRole
import cc.kowx712.autohoyolab.data.model.RedeemResult
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class RedeemRunner(
    private val apiClient: HoyoLabApiClient,
    private val database: AppDatabase,
) {

    suspend fun redeem(game: HoyoGame, role: HoyoGameRole) {
        if (!game.supportRedeem || game.redeemUrl == null) return

        try {
            val codes = apiClient.fetchPromoCodes(game) ?: return
            if (codes.isEmpty()) return

            val alreadyRedeemed = database.redeemedCodeDao().getRedeemedCodes(game.id).toHashSet()
            val pending = codes.distinct()
                .filter { it !in alreadyRedeemed }
                .take(MAX_CODES_PER_CYCLE)
            if (pending.isEmpty()) return

            var successCount = 0
            for ((index, code) in pending.withIndex()) {
                if (index > 0) delay(REDEEM_INTERVAL)

                when (val result = apiClient.redeemCode(game, role.gameUid, role.region, code)) {
                    is RedeemResult.NetworkError -> break
                    is RedeemResult.Cooldown -> break
                    is RedeemResult.CredentialError -> break
                    is RedeemResult.LevelTooLow -> Unit
                    else -> {
                        database.redeemedCodeDao().insert(
                            RedeemedCode(
                                gameId = game.id,
                                code = code,
                                gameUid = role.gameUid,
                                redeemedAt = System.currentTimeMillis(),
                            )
                        )
                        if (result is RedeemResult.Success) {
                            successCount++
                        }
                    }
                }
            }

            if (successCount > 0) {
                Log.d(TAG, "${game.displayName}: Redeemed $successCount promo codes")
                database.checkInLogDao().insert(
                    CheckInLog(
                        gameId = game.id,
                        gameUid = role.gameUid,
                        region = role.region,
                        timestamp = System.currentTimeMillis(),
                        status = STATUS_REDEEMED,
                        message = null,
                        retcode = null,
                        redeemCount = successCount,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Redeem failed for ${game.displayName}: ${e::class.simpleName}: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "RedeemRunner"
        private const val STATUS_REDEEMED = "REDEEMED"
        private val REDEEM_INTERVAL = 6.seconds
        private const val MAX_CODES_PER_CYCLE = 40
    }
}
