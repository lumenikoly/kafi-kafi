package com.lightkafka.core.storage

interface ProfileStore {
    fun loadProfiles(): List<ClusterProfile>

    fun saveProfiles(profiles: List<ClusterProfile>)

    fun upsertProfile(profile: ClusterProfile)

    fun deleteProfile(profileId: String)

    fun loadProfileNames(): List<String> = loadProfiles().map(ClusterProfile::name)
}
