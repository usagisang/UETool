package me.ele.uetool.base;

import android.graphics.Rect;
import android.os.Build;
import android.view.View;

public class Element {

    private View view;
    private Rect originRect = new Rect();
    private Rect rect = new Rect();
    private int[] location = new int[2];
    private Element parentElement;

    public Element(View view) {
        this(view, null);
    }

    public Element(View view, Rect rect) {
        this.view = view;
        if (rect == null) {
            reset();
        } else {
            this.rect.set(rect);
        }
        originRect.set(this.rect.left, this.rect.top, this.rect.right, this.rect.bottom);
    }

    public View getView() {
        return view;
    }

    public Rect getRect() {
        return rect;
    }

    public Rect getOriginRect() {
        return originRect;
    }

    public void reset() {
        view.getLocationOnScreen(location);
        int width = view.getWidth();
        int height = view.getHeight();

        int left = location[0];
        int right = left + width;
        int top = location[1];
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            top -= DimenUtil.getStatusBarHeight();
        }
        int bottom = top + height;

        rect.set(left, top, right, bottom);
    }

    public Element getParentElement() {
        if (parentElement == null) {
            Object parentView = view.getParent();
            if (parentView instanceof View) {
                parentElement = new Element((View) parentView);
            }
        }
        return parentElement;
    }

    public void setParentElement(Element parentElement) {
        this.parentElement = parentElement;
    }

    public boolean isVirtual() {
        return false;
    }

    public String getElementName() {
        return view.getClass().getName();
    }

    //  view 的面积
    public int getArea() {
        return rect.width() * rect.height();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Element element = (Element) o;

        return view != null ? view.equals(element.view) : element.view == null;

    }

    @Override
    public int hashCode() {
        return view != null ? view.hashCode() : 0;
    }
}
