package cc.kowx712.autohoyolab.data.model

sealed class RedeemResult(open val gameId: String, open val code: String) {
    data class Success(override val gameId: String, override val code: String) : RedeemResult(gameId, code)
    data class AlreadyRedeemed(override val gameId: String, override val code: String) : RedeemResult(gameId, code)

    data class Failed(
        override val gameId: String,
        override val code: String,
        val message: String,
        val retcode: Int?,
    ) : RedeemResult(gameId, code)

    data class LevelTooLow(override val gameId: String, override val code: String) : RedeemResult(gameId, code)

    data class Cooldown(override val gameId: String, override val code: String) : RedeemResult(gameId, code)

    data class CredentialError(
        override val gameId: String,
        override val code: String,
        val message: String,
        val retcode: Int?,
    ) : RedeemResult(gameId, code)

    data class NetworkError(override val gameId: String, override val code: String) : RedeemResult(gameId, code)
}
