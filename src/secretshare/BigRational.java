package secretshare;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;


/**
 * Immutable, arbitrary-precision signed rational numbers.
 *
 *  Invariants
 *  ----------
 *   o  GCD(numerator, denominator) = 1, i.e., rational number is in reduced form
 *   o  denominator >= 1, i.e., the denominator is always a positive integer
 *   o  BigRational(0,1) is the unique representation of zero
 *
 *
 */
public class BigRational extends Number implements Comparable<BigRational>
{
    // ==================================================
    // class static data
    // ==================================================
    private static final long serialVersionUID = -1035898074640211021L;

    public final static BigRational ZERO = new BigRational(0, 1);
    public final static BigRational ONE = new BigRational(1, 1);

    // ==================================================
    // class static methods
    // ==================================================

    // ==================================================
    // instance data
    // ==================================================

    private BigInteger numerator;
    private BigInteger denominator;

    // ==================================================
    // factories
    // ==================================================


    public BigRational(BigInteger numerator, BigInteger denominator)
    {
        init(numerator, denominator);
    }

    public BigRational(int numerator, int denominator)
    {
        this(new BigInteger("" + numerator), new BigInteger("" + denominator));
    }

    public BigRational(int numerator)
    {
        this(numerator, 1);
    }

    /**
     * @param s string of rational number, e.g. "-105/3433"
     */
    public BigRational(String s)
    {
        String[] tokens = s.split("/");
        if (tokens.length == 2)
        {
            init(new BigInteger(tokens[0]), new BigInteger(tokens[1]));
        }
        else if (tokens.length == 1)
        {
            init(new BigInteger(tokens[0]), BigInteger.ONE);
        }
        else
        {
            throw new IllegalArgumentException("Bad format for rational number '" + s + "'");
        }
    }

    private void init(BigInteger num, BigInteger denom)
    {
        if (denom.equals(BigInteger.ZERO))
        {
            throw new ArithmeticException("Denominator is zero");
        }

        // normalize fraction
        BigInteger g = num.gcd(denom);
        numerator = num.divide(g);
        denominator = denom.divide(g);

        // ensure that denominator is positive
        if (denominator.compareTo(BigInteger.ZERO) < 0)
        {
            denominator = denominator.negate();
            numerator = numerator.negate();
        }
    }

    public BigInteger getNumerator() {
        return numerator;
    }

    public BigInteger getDenominator() {
        return denominator;
    }

    @Override
    public String toString()
    {
        if (denominator.equals(BigInteger.ONE))
        {
            return numerator + "";
        }
        else
        {
            return numerator + "/" + denominator;
        }
    }

    // return { -1, 0, + 1 } if a < b, a = b, or a > b
    @Override
    public int compareTo(BigRational b)
    {
        BigRational a = this;
        return a.numerator.multiply(b.denominator).compareTo(a.denominator.multiply(b.numerator));
    }

    public boolean isZero()
    {
        return compareTo(ZERO) == 0;
    }

    public boolean isPositive()
    {
        return compareTo(ZERO)  > 0;
    }

    public boolean isNegative()
    {
        return compareTo(ZERO)  < 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BigRational other = (BigRational) obj;
        if (denominator == null) {
            if (other.denominator != null)
                return false;
        } else if (!denominator.equals(other.denominator))
            return false;
        if (numerator == null) {
            if (other.numerator != null)
                return false;
        } else if (!numerator.equals(other.numerator))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((denominator == null) ? 0 : denominator.hashCode());
        result = prime * result
                + ((numerator == null) ? 0 : numerator.hashCode());
        return result;
    }

    /**
     * @return this * b
     */
    public BigRational multiply(BigRational b)
    {
        BigRational a = this;
        return new BigRational(a.numerator.multiply(b.numerator), a.denominator.multiply(b.denominator));
    }

    /**
     * @return this + b
     */
    public BigRational add(BigRational b)
    {
        BigRational a = this;
        BigInteger numerator   = a.numerator.multiply(b.denominator).add(b.numerator.multiply(a.denominator));
        BigInteger denominator = a.denominator.multiply(b.denominator);
        return new BigRational(numerator, denominator);
    }

    /**
     * @return -this
     */
    public BigRational negate()
    {
        return new BigRational(numerator.negate(), denominator);
    }

    /**
     * @return this - b
     */
    public BigRational subtract(BigRational b)
    {
        BigRational a = this;
        return a.add(b.negate());
    }

    /**
     * @return 1 / this
     */
    public BigRational reciprocal()
    {
        return new BigRational(denominator, numerator);
    }

    /**
     *  @return this / b
     */
    public BigRational divide(BigRational b)
    {
        BigRational a = this;
        return a.multiply(b.reciprocal());
    }


    @Override
    public int intValue()
    {
        return (int) doubleValue();
    }

    @Override
    public long longValue()
    {
        return (long) doubleValue();
    }

    @Override
    public float floatValue()
    {
        return (float) doubleValue();
    }

    @Override
    public double doubleValue()
    {
        int SCALE = 32;        // number of digits after the decimal place
        BigDecimal retnumerator   = new BigDecimal(numerator);
        BigDecimal retdenominator = new BigDecimal(denominator);
        BigDecimal quotient    = retnumerator.divide(retdenominator, SCALE, RoundingMode.HALF_EVEN);
        return quotient.doubleValue();
    }


}
