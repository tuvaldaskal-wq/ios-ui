package com.ioslauncher;

/**
 * Tiny self-contained arithmetic evaluator for the Spotlight calculator.
 * Supports + - * / %, ^ (power), parentheses, unary minus and decimals.
 * Returns null when the input is not a valid standalone expression.
 */
public final class MathEval {

    private final String s;
    private int pos = -1;
    private int ch;
    private boolean sawOperator;

    private MathEval(String str) {
        this.s = str;
    }

    public static Double eval(String expr) {
        if (expr == null) {
            return null;
        }
        String t = expr.trim();
        if (t.length() == 0) {
            return null;
        }
        // Require at least one digit and only math characters.
        boolean hasDigit = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if ("+-*/%^(). ".indexOf(c) < 0) {
                return null;
            }
        }
        if (!hasDigit) {
            return null;
        }
        try {
            MathEval m = new MathEval(t);
            m.nextChar();
            double v = m.parseExpression();
            if (m.pos < m.s.length()) {
                return null;
            }
            if (!m.sawOperator) {
                return null; // a bare number is not a "calculation"
            }
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return null;
            }
            return Double.valueOf(v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void nextChar() {
        ch = (++pos < s.length()) ? s.charAt(pos) : -1;
    }

    private boolean eat(int charToEat) {
        while (ch == ' ') {
            nextChar();
        }
        if (ch == charToEat) {
            nextChar();
            return true;
        }
        return false;
    }

    private double parseExpression() {
        double x = parseTerm();
        for (;;) {
            if (eat('+')) {
                sawOperator = true;
                x += parseTerm();
            } else if (eat('-')) {
                sawOperator = true;
                x -= parseTerm();
            } else {
                return x;
            }
        }
    }

    private double parseTerm() {
        double x = parseFactor();
        for (;;) {
            if (eat('*')) {
                sawOperator = true;
                x *= parseFactor();
            } else if (eat('/')) {
                sawOperator = true;
                x /= parseFactor();
            } else if (eat('%')) {
                sawOperator = true;
                x %= parseFactor();
            } else {
                return x;
            }
        }
    }

    private double parseFactor() {
        if (eat('+')) {
            return parseFactor();
        }
        if (eat('-')) {
            return -parseFactor();
        }
        double x;
        int startPos = pos;
        if (eat('(')) {
            x = parseExpression();
            if (!eat(')')) {
                throw new RuntimeException("missing )");
            }
        } else if ((ch >= '0' && ch <= '9') || ch == '.') {
            while ((ch >= '0' && ch <= '9') || ch == '.') {
                nextChar();
            }
            x = Double.parseDouble(s.substring(startPos, pos));
        } else {
            throw new RuntimeException("unexpected");
        }
        if (eat('^')) {
            sawOperator = true;
            x = Math.pow(x, parseFactor());
        }
        return x;
    }
}
