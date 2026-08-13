signingConfigs {
    create("release") {
        // 优先从环境变量读取，兼容 CI 和本地
        val storeFilePath = System.getenv("SIGNING_STORE_FILE") ?: "release.jks"
        storeFile = file(storeFilePath)
        
        storePassword = System.getenv("SIGNING_STORE_PASSWORD") 
            ?: System.getenv("STORE_PASSWORD") 
            ?: "questionhelper"
            
        keyAlias = System.getenv("SIGNING_KEY_ALIAS") 
            ?: System.getenv("KEY_ALIAS") 
            ?: "questionhelper"
            
        keyPassword = System.getenv("SIGNING_KEY_PASSWORD") 
            ?: System.getenv("KEY_PASSWORD") 
            ?: "questionhelper"
    }
}
