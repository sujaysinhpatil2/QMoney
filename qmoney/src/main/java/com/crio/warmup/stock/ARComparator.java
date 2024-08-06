package com.crio.warmup.stock;

import java.util.Comparator;
import com.crio.warmup.stock.dto.AnnualizedReturn;

public class ARComparator implements Comparator<AnnualizedReturn> {

    @Override
    public int compare(AnnualizedReturn a1, AnnualizedReturn a2) {
        if(a1.getAnnualizedReturn() < a2.getAnnualizedReturn()) return 1;
        else if(a1.getAnnualizedReturn() > a2.getAnnualizedReturn()) return -1;
        else return 0;
    }
    
}
