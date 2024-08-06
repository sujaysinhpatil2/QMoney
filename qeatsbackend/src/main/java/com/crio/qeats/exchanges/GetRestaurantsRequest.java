/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.exchanges;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetRestaurantsRequest {

    private String city;

    private String searchFor;

    @NotNull
    @Max(90)
    @Min(-90)
    private Double latitude;

    @NotNull
    @Max(180)
    @Min(-180)
    private Double longitude;
    
    public GetRestaurantsRequest(@NotNull Double latitude, @NotNull Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

}

