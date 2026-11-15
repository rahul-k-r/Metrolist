package com.metrolist.shared.update

data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>,
    // Deliberately outside SemVer: build metadata orders fork releases that share an upstream version.
    val build: List<String> = emptyList(),
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
        minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
        patch.compareTo(other.patch).takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
        comparePrecedence(prerelease, other.prerelease).takeIf { it != 0 }?.let { return it }
        return comparePrecedence(build, other.build)
    }

    private fun comparePrecedence(
        left: List<String>,
        right: List<String>,
    ): Int {
        repeat(maxOf(left.size, right.size)) { index ->
            val l = left.getOrNull(index) ?: return -1
            val r = right.getOrNull(index) ?: return 1
            val leftNumber = l.toIntOrNull()
            val rightNumber = r.toIntOrNull()
            val comparison =
                when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> l.compareTo(r)
                }
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private val pattern = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$")

        fun parse(value: String): ReleaseVersion? {
            val match = pattern.matchEntire(value) ?: return null
            return ReleaseVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                prerelease =
                    match.groupValues[4]
                        .takeIf(String::isNotEmpty)
                        ?.replace(Regex("(?i)^(alpha|beta|rc)([0-9]+)(?=\\.|$)"), "$1.$2")
                        ?.split('.')
                        .orEmpty(),
                build = match.groupValues[5].takeIf(String::isNotEmpty)?.split('.').orEmpty(),
            )
        }
    }
}
