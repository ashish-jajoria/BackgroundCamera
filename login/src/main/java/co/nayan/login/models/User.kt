package co.nayan.login.models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class User(
    val id: Int,
    val email: String?,
    val uid: String?,
    val name: String?,
): Parcelable
