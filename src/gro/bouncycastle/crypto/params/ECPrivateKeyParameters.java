package gro.bouncycastle.crypto.params;

import ewe.math.BigInteger;

public class ECPrivateKeyParameters
    extends ECKeyParameters
{
    BigInteger d;

    public ECPrivateKeyParameters(
        BigInteger          d,
        ECDomainParameters  params)
    {
        super(true, params);
        this.d = d;
    }

    public BigInteger getD()
    {
        return d;
    }
}
