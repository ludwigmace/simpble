package secretshare;

import java.math.BigInteger;

/**
 * Holds all the info needed to be a "piece" of the secret.
 * aka a "Share" of the secret.
 *
 * @author tiemens
 *
 */
public class ShareInfo
{
    // Identity fields:
    private final int x;              // this is aka "the index", the x in "f(x)"
    private final BigInteger share;   // our piece of the secret

    // technically"extra" - at least one ShareInfo must have a PublicInfo,
    //                      but it is not required that every ShareInfo has a PublicInfo
    // But for simplicity, it is a required field:
    private final PublicInfo publicInfo;

    public ShareInfo(final int inX,
                     final BigInteger inShare,
                     final PublicInfo inPublicInfo)
    {
        if (inShare == null)
        {
            throw new SecretShareException("share cannot be null");
        }
        if (inPublicInfo == null)
        {
            throw new SecretShareException("publicinfo cannot be null");
        }

        x = inX;
        share = inShare;
        publicInfo = inPublicInfo;
    }
    public String debugDump()
    {
        return "ShareInfo[x=" + x + "\n" +
                "share=" + share + "\n" +
                "shareBigIntCs=" + BigIntStringChecksum.create(share).toString() + "\n" +
                " public=" + publicInfo.debugDump() +
                "]";
    }
    public final int getIndex()
    {
        return x;
    }
    public final int getX()
    {
        return x;
    }
    public final BigInteger getXasBigInteger()
    {
        return BigInteger.valueOf(x);
    }
    public final BigInteger getShare()
    {
        return share;
    }
    public final PublicInfo getPublicInfo()
    {
        return publicInfo;
    }
    @Override
    public int hashCode()
    {
        // Yes, this is a terrible implementation.   But it is correct.
        return x;
    }
    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ShareInfo)
        {
            return equalsType((ShareInfo) obj);
        }
        else
        {
            return false;
        }
    }

    public boolean equalsType(ShareInfo other)
    {
        // NOTE: equality of a ShareInfo is based on:
        //  1.  x
        //  2.  f(x)
        //  3.  k
        return ((this.x == other.x)  &&
                (this.share.equals(other.share)) &&
                (this.publicInfo.k == other.publicInfo.k)
               );
    }
}