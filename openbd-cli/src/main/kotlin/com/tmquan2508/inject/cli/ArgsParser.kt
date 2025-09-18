package com.tmquan2508.inject.cli

fun findArg(long: String, short: String?, args: List<String>): String? {
    val longFlag = "--$long"
    val shortFlag = short?.let { "-$it" }

    val longIndex = args.indexOf(longFlag)
    if (longIndex != -1 && longIndex + 1 < args.size) {
        return args[longIndex + 1]
    }

    if (shortFlag != null) {
        val shortIndex = args.indexOf(shortFlag)
        if (shortIndex != -1 && shortIndex + 1 < args.size) {
            return args[shortIndex + 1]
        }
    }

    return null
}

fun boolArg(long: String, short: String?, args: List<String>): Boolean {
    val longFlag = "--$long"
    val shortFlag = short?.let { "-$it" }

    return args.contains(longFlag) || (shortFlag != null && args.contains(shortFlag))
}