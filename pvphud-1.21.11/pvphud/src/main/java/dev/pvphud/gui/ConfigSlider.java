package dev.pvphud.gui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public class ConfigSlider extends SliderWidget {
    private final double min, max, step;
    private final DoubleConsumer setter;
    private final DoubleFunction<String> fmt;

    public ConfigSlider(int width, double min, double max, double step, double value, DoubleConsumer setter, DoubleFunction<String> fmt) {
        super(0, 0, width, 18, Text.empty(), (Math.max(min, Math.min(max, value)) - min) / (max - min));
        this.min = min; this.max = max; this.step = step; this.setter = setter; this.fmt = fmt;
        updateMessage();
    }

    private double current() {
        double v = min + value * (max - min);
        if (step > 0) v = Math.round(v / step) * step;
        return Math.max(min, Math.min(max, v));
    }

    @Override protected void updateMessage() { setMessage(Text.literal(fmt.apply(current()))); }
    @Override protected void applyValue() { setter.accept(current()); }
}
