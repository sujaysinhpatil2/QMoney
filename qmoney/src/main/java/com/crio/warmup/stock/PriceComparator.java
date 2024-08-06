package com.crio.warmup.stock;

import java.util.Comparator;
import com.crio.warmup.stock.dto.TotalReturnsDto;

public class PriceComparator implements Comparator<TotalReturnsDto> {

    @Override
    public int compare(TotalReturnsDto trd1, TotalReturnsDto trd2) {
        if(trd1.getClosingPrice() > trd2.getClosingPrice()) return 1;
        else if(trd1.getClosingPrice() < trd2.getClosingPrice()) return -1;
        else return 0;
    }
    
}
