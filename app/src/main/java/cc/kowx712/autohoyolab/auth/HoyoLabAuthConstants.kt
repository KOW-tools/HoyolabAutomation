package cc.kowx712.autohoyolab.auth

/**
 * Constants for HoYoLAB authentication
 */
object HoyoLabAuthConstants {

    // API Endpoints
    const val APP_LOGIN_URL = "https://sg-public-api.hoyoverse.com/account/ma-passport/api/appLoginByPassword"
    const val COOKIE_V2_REFRESH_URL = "https://sg-public-api.hoyoverse.com/account/ma-passport/token/getBySToken"
    const val EMAIL_CAPTCHA_REQUEST_URL = "https://sg-public-api.hoyoverse.com/account/ma-verifier/api/createEmailCaptchaByActionTicket"
    const val EMAIL_CAPTCHA_VERIFY_URL = "https://sg-public-api.hoyoverse.com/account/ma-verifier/api/verifyActionTicketPartly"

    // Salt Values
    object Salts {
        const val OVERSEAS = "6s25p5ox5y14umn1p61aqyyvbvvl3lrt"
        const val CHINESE = "xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs"
        const val APP_LOGIN = "IZPgfb0dRPtBeLuFkdDznSZ6f4wWt6y2"
        const val CN_SIGNIN = "LyD1rXqMv2GJhnwdvCBjFOKGiKuLY3aO"
        const val CN_PASSPORT = "JwYDpKvLj6MrMqqYU6jTKF17KNO2PXoS"
    }

    // RSA Public Key for password encryption
    val LOGIN_RSA_PUBLIC_KEY = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4PMS2JVMwBsOIrYWRluY
        wEiFZL7Aphtm9z5Eu/anzJ09nB00uhW+ScrDWFECPwpQto/GlOJYCUwVM/raQpAj
        /xvcjK5tNVzzK94mhk+j9RiQ+aWHaTXmOgurhxSp3YbwlRDvOgcq5yPiTz0+kSeK
        ZJcGeJ95bvJ+hJ/UMP0Zx2qB5PElZmiKvfiNqVUk8A8oxLJdBB5eCpqWV6CUqDKQ
        KSQP4sM0mZvQ1Sr4UcACVcYgYnCbTZMWhJTWkrNXqI8TMomekgny3y+d6NX/cFa6
        6jozFIF4HCX5aW8bp8C8vq2tFvFbleQ/Q3CU56EWWKMrOcpmFtRmC18s9biZBVR/
        8QIDAQAB
        -----END PUBLIC KEY-----
    """.trimIndent()
}
