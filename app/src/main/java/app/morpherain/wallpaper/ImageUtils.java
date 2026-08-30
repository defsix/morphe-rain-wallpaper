package app.morpherain.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

final class ImageUtils {
    private ImageUtils() {}

    static Bitmap decodeOrientedBitmap(Context context, Uri uri, int maxSide) throws IOException {
        if (context == null || uri == null) return null;

        int orientation = readExifOrientation(context, uri);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int limit = Math.max(1, maxSide);
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > limit) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) return null;

        Matrix matrix = exifMatrix(orientation);
        if (matrix == null) return bitmap;

        try {
            Bitmap oriented = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );
            if (oriented != bitmap) bitmap.recycle();
            return oriented;
        } catch (OutOfMemoryError error) {
            return bitmap;
        }
    }

    private static int readExifOrientation(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return ExifInterface.ORIENTATION_NORMAL;
            ExifInterface exif = new ExifInterface(input);
            return exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
        } catch (Exception ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Matrix exifMatrix(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_UNDEFINED:
            default:
                return null;
        }
        return matrix;
    }
}
