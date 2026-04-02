def getAccountId(String environment) {
    switch (environment) {
        case "dev":
            return "017183880052"
        case "qa":
            return "017183880052"
        case "uat":
            return "017183880052"
        case "pre-prod":
            return "017183880052"
        case "prod":
            return "017183880052"
        default:
            throw new IllegalArgumentException("Unknown environment: ${envName}")
    }
}