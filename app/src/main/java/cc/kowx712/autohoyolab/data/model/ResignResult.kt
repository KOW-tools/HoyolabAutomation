package cc.kowx712.autohoyolab.data.model

sealed class ResignResult(open val gameId: String) {
    data class Success(override val gameId: String) : ResignResult(gameId)
    data class NotSupported(override val gameId: String) : ResignResult(gameId)
    data class NotEligible(override val gameId: String, val reason: String) : ResignResult(gameId)
    data class Failed(
        override val gameId: String,
        val message: String,
        val retcode: Int?,
    ) : ResignResult(gameId)

    data class CookieExpired(override val gameId: String) : ResignResult(gameId)
    data class NetworkError(override val gameId: String) : ResignResult(gameId)
}
