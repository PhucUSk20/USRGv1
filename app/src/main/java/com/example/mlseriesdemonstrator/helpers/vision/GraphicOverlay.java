package com.example.mlseriesdemonstrator.helpers.vision;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.camera.core.CameraSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * A view which renders a series of custom graphics to be overlaid on top of an associated preview
 * (i.e., the camera preview). The creator can add graphics objects, update the objects, and remove
 * them, triggering the appropriate drawing and invalidation within the view.
 * Chế độ xem hiển thị một loạt đồ họa tùy chỉnh được phủ lên trên bản xem trước được liên kết
 *  (tức là bản xem trước của máy ảnh). Người tạo có thể thêm các đối tượng đồ họa, cập nhật các đối tượng và xóa
 *  chúng, kích hoạt bản vẽ thích hợp và vô hiệu hóa trong chế độ xem.
 */
public class GraphicOverlay extends View {
  private final Object lock = new Object();
  private final List<Graphic> graphics = new ArrayList<>();

  public int previewWidth;
  public int previewHeight;
  public boolean isLensFacingFront;

  /**
   * Base class for a custom graphics object to be rendered within the graphic overlay. Subclass
   * this and implement the {@link Graphic#draw(Canvas)} method to define the graphics element. Add
   * instances to the overlay using {@link GraphicOverlay#add(Graphic)}.
   * Lớp cơ sở dành cho đối tượng đồ họa tùy chỉnh được hiển thị trong lớp phủ đồ họa. Lớp con
   * cái này và triển khai phương thức {@link Graphic#draw(Canvas)} để xác định thành phần đồ họa. Thêm vào
   * phiên bản lớp phủ bằng cách sử dụng {@link GraphicOverlay#add(Graphic)}.
   */
  public abstract static class Graphic {

    private final int imageWidth;

    private final int imageHeight;

    private final GraphicOverlay overlay;

    public Graphic(GraphicOverlay overlay, int width, int height) {
      this.overlay = overlay;
      imageWidth = width;
      imageHeight = height;
    }

    /**
     * Draw the graphic on the supplied canvas. Drawing should use the following methods to convert
     * to view coordinates for the graphics that are drawn:
     *
     * <ol>
     *   <li>{@link Graphic#translateX(float)} and {@link Graphic#translateY(float)} adjust the
     *       coordinate from the image's coordinate system to the view coordinate system.
     * </ol>
     *
     * @param canvas drawing canvas
     *
     * * Vẽ đồ họa trên canvas được cung cấp. Bản vẽ nên sử dụng các phương pháp sau để chuyển đổi
     *      * để xem tọa độ của đồ họa được vẽ:
     *      *
     *      * <ol>
     *      * <li>{@link Graphic#translateX(float)} và {@link Graphic#translateY(float)} điều chỉnh
     *      * tọa độ từ hệ tọa độ của hình ảnh đến hệ tọa độ xem.
     *      * </ol>
     *      *
     *      * @param canvas vẽ canvas
     */
    public abstract void draw(Canvas canvas);

    public RectF transform(Rect rect) {
      float scaleX = overlay.previewWidth / (float) imageWidth;
      float scaleY = overlay.previewHeight / (float) imageHeight;
      // If the front camera lens is being used, reverse the right/left coordinates
      float flippedLeft;
      if (overlay.isLensFacingFront)
        flippedLeft = imageWidth - rect.right;
      else
        flippedLeft = rect.left;
      float flippedRight;
      if (overlay.isLensFacingFront)
        flippedRight = imageWidth - rect.left;
      else
        flippedRight = rect.right;

      // Scale all coordinates to match preview
      float scaledLeft = scaleX * flippedLeft;
      float scaledTop = scaleY * rect.top;
      float scaledRight = scaleX * flippedRight;
      float scaledBottom = scaleY * rect.bottom;

      return new RectF(scaledLeft, scaledTop, scaledRight, scaledBottom);
    }

    /**
     * Adjusts the x coordinate from the image's coordinate system to the view coordinate system.
     * Điều chỉnh tọa độ x từ hệ tọa độ của hình ảnh sang hệ tọa độ khung nhìn.
     */
    public float translateX(float x) {
      float scaleX = overlay.previewWidth / (float) imageWidth;

      float flippedX;
      if (overlay.isLensFacingFront) {
        flippedX = imageWidth - x;
      } else {
        flippedX = x;
      }
      return flippedX * scaleX;
    }

    /**
     * Adjusts the y coordinate from the image's coordinate system to the view coordinate system.
     * Điều chỉnh tọa độ y từ hệ tọa độ của hình ảnh sang hệ tọa độ khung nhìn.
     */
    public float translateY(float y) {
      float scaleY = overlay.previewHeight / (float) imageHeight;
      return y * scaleY;
    }

    public void postInvalidate() {
      overlay.postInvalidate();
    }
  }

  public GraphicOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    addOnLayoutChangeListener(
        (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
            postInvalidate());
  }

  /** Removes all graphics from the overlay. */
  public void clear() {
    synchronized (lock) {
      graphics.clear();
    }
    postInvalidate();
  }

  /** Adds a graphic to the overlay. */
  public void add(Graphic graphic) {
    synchronized (lock) {
      graphics.add(graphic);
    }
    postInvalidate();
  }

  /** Removes a graphic from the overlay. */
  public void remove(Graphic graphic) {
    synchronized (lock) {
      graphics.remove(graphic);
    }
    postInvalidate();
  }

  /** Draws the overlay with its associated graphic objects. */
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    synchronized (lock) {
      for (Graphic graphic : graphics) {
        graphic.draw(canvas);
      }
    }
  }

  public void setPreviewProperties(int previewWidth, int previewHeight, int lensFacing) {
    this.previewWidth = previewWidth;
    this.previewHeight = previewHeight;
    this.isLensFacingFront = CameraSelector.LENS_FACING_FRONT == lensFacing;
  }
}
