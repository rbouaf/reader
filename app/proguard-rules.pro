# PdfBox (used only to read PDF outlines): the optional JPEG 2000 codec isn't bundled
-dontwarn com.gemalto.jp2.**
# PdfBox loads resources and some classes reflectively
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
