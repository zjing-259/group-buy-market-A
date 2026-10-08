package cn.bugstack.types.common;

/** Cut range is an integer percentage in [0, 100]. */
public final class CutRangePolicy {
    private CutRangePolicy() { }

    public static int parse(String value) {
        if (value == null || !value.matches("(?:100|[1-9]?\\d)")) {
            throw new IllegalArgumentException("cutRange must be an integer from 0 to 100");
        }
        return Integer.parseInt(value);
    }
}
