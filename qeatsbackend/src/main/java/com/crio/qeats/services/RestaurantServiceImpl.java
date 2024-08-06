package com.crio.qeats.services;

import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class RestaurantServiceImpl implements RestaurantService {

  private final Double peakHoursServingRadiusInKms = 3.0;
  private final Double normalHoursServingRadiusInKms = 5.0;
  
  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;

  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
      // long startTimeInMillis = System.currentTimeMillis();
      List<Restaurant> restaurantList = null;
      GetRestaurantsResponse getRestaurantsResponse = new GetRestaurantsResponse(restaurantList);
      
      if (isPeakHour(currentTime)) {
        restaurantList = restaurantRepositoryService.findAllRestaurantsCloseBy(
          getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(), currentTime, peakHoursServingRadiusInKms);
      } else {
        restaurantList = restaurantRepositoryService.findAllRestaurantsCloseBy(
          getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(), currentTime, normalHoursServingRadiusInKms);
      }
      getRestaurantsResponse.setRestaurants(restaurantList);
      log.info(getRestaurantsResponse);
      // long endTimeInMillis = System.currentTimeMillis();
      // System.out.println("Time taken by function at Service is : " +(endTimeInMillis-startTimeInMillis));
     return getRestaurantsResponse;
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Implement findRestaurantsBySearchQuery. The request object has the search string.
  // We have to combine results from multiple sources:
  // 1. Restaurants by name (exact and inexact)
  // 2. Restaurants by cuisines (also called attributes)
  // 3. Restaurants by food items it serves
  // 4. Restaurants by food item attributes (spicy, sweet, etc)
  // Remember, a restaurant must be present only once in the resulting list.
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQuery(GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
      if(getRestaurantsRequest.getSearchFor() == "") {
        List<Restaurant> restaurantList = new ArrayList<>();
        GetRestaurantsResponse getRestaurantsResponse = new GetRestaurantsResponse(restaurantList);
        return getRestaurantsResponse;
      }
      Double latitude = getRestaurantsRequest.getLatitude();
      Double longitude = getRestaurantsRequest.getLongitude();
      String searchString = getRestaurantsRequest.getSearchFor();
      Double servingRadiusInKms = 0.0;
      if(isPeakHour(currentTime)) servingRadiusInKms = 3.0;
      else servingRadiusInKms = 5.0;
      Set<Restaurant> linkedHashSet = new LinkedHashSet<>();
      List<Restaurant> restaurantListByName = restaurantRepositoryService.findRestaurantsByName(latitude, longitude, searchString, currentTime, servingRadiusInKms);
      List<Restaurant> restaurantsByNameAttributes = restaurantRepositoryService.findRestaurantsByAttributes(latitude, longitude, searchString, currentTime, servingRadiusInKms);
      List<Restaurant> findRestaurantsByItemName = restaurantRepositoryService.findRestaurantsByItemName(latitude, longitude, searchString, currentTime, servingRadiusInKms);
      List<Restaurant> findRestaurantsByItemAttributes = restaurantRepositoryService.findRestaurantsByItemAttributes(latitude, longitude, searchString, currentTime, servingRadiusInKms);
      restaurantListByName.addAll(restaurantsByNameAttributes);
      restaurantListByName.addAll(findRestaurantsByItemName);
      restaurantListByName.addAll(findRestaurantsByItemAttributes);
      for (Restaurant r : restaurantListByName) {
        linkedHashSet.add(r);
      }
      List<Restaurant> restaurantList = new ArrayList<>(linkedHashSet);
      GetRestaurantsResponse getRestaurantsResponse = new GetRestaurantsResponse(restaurantList);
      return getRestaurantsResponse;
  }

  private boolean isPeakHour(LocalTime currentTime) {
      LocalTime s1 = LocalTime.of(7, 59, 59);
      LocalTime e1 = LocalTime.of(10, 0, 1);
      LocalTime s2 = LocalTime.of(12, 59, 59);
      LocalTime e2 = LocalTime.of(14, 0, 1);
      LocalTime s3 = LocalTime.of(18, 59,59);
      LocalTime e3 = LocalTime.of(21, 0, 1);
      if( 
          (currentTime.isAfter(s1)&&currentTime.isBefore(e1)) || 
          (currentTime.isAfter(s2)&&currentTime.isBefore(e2)) ||
          (currentTime.isAfter(s3)&&currentTime.isBefore(e3)) ) {
            return true;
        }

    return false;
  }

  // TODO: CRIO_TASK_MODULE_MULTITHREADING
  // Implement multi-threaded version of RestaurantSearch.
  // Implement variant of findRestaurantsBySearchQuery which is at least 1.5x time faster than
  // findRestaurantsBySearchQuery.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQueryMt(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
      Double servingRadiusInKms = isPeakHour(currentTime) ? peakHoursServingRadiusInKms :
      normalHoursServingRadiusInKms;
      String searchFor = getRestaurantsRequest.getSearchFor();
      List<Restaurant> restaurantList;
      if (!searchFor.isEmpty()) {
        long startTime = System.currentTimeMillis();
        Future<List<Restaurant>> futureGetRestaurantsByNameList
        = restaurantRepositoryService.findRestaurantsByNameAsync(
        getRestaurantsRequest.getLatitude(),
        getRestaurantsRequest.getLongitude(),
        searchFor, currentTime, servingRadiusInKms);
        Future<List<Restaurant>> futureGetRestaurantsByAttributesList =
        restaurantRepositoryService
        .findRestaurantsByAttributesAsync(getRestaurantsRequest.getLatitude(),
        getRestaurantsRequest.getLongitude(), searchFor,
        currentTime, servingRadiusInKms);
        List<Restaurant> restaurantsByNameList;
        List<Restaurant> restaurantByAttributesList;
        try {
          while (true) {
            if (futureGetRestaurantsByNameList.isDone() && futureGetRestaurantsByAttributesList.isDone()) {
              restaurantsByNameList = futureGetRestaurantsByNameList.get();
              restaurantByAttributesList = futureGetRestaurantsByAttributesList.get();
              log.info("Time in millis: " + (System.currentTimeMillis() - startTime));
              break;
            }
          }
        } catch (InterruptedException | ExecutionException e) {
          e.printStackTrace();
          return new GetRestaurantsResponse(new ArrayList<>());
        }
        Map<String, Restaurant> restaurantMap = new HashMap<>();
        for (Restaurant restaurant : restaurantsByNameList) {
          restaurantMap.put(restaurant.getRestaurantId(), restaurant);
        } 
        for (Restaurant restaurant : restaurantByAttributesList) {
          restaurantMap.put(restaurant.getRestaurantId(), restaurant);
        }
        restaurantList = new ArrayList<>(restaurantMap.values());
      } else {
        restaurantList = new ArrayList<>();
      }
    return new GetRestaurantsResponse(restaurantList);
  }
  
}
