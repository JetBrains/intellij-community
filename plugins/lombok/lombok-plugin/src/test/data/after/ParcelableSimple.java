class ParcelableSimple implements android.os.Parcelable {
  public int describeContents() {
    return 0;
  }
  public void writeToParcel(android.os.Parcel dest, int flags) {
  }
  protected ParcelableSimple(android.os.Parcel source) {
  }
  public static final android.os.Parcelable.Creator<ParcelableSimple> CREATOR;
}

final class ParcelableSimpleFinal implements android.os.Parcelable {
  public int describeContents() {
    return 0;
  }
  public void writeToParcel(android.os.Parcel dest, int flags) {
  }
  ParcelableSimpleFinal(android.os.Parcel source) {
  }
  public static final android.os.Parcelable.Creator<ParcelableSimpleFinal> CREATOR;
}
