package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Item;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.models.ItemEntity;
import com.crio.qeats.models.MenuEntity;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.ItemRepository;
import com.crio.qeats.repositories.MenuRepository;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoLocation;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.KeyStore.Entry.Attribute;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

@Service
@Primary
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private MenuRepository menuRepository;

  @Autowired
  private RedisConfiguration redisConfiguration;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objective:
  // 1. Check if a restaurant is nearby and open. If so, it is a candidate to be returned.
  // NOTE: How far exactly is "nearby"?
  // 2. Remember to keep the precision of GeoHash in mind while using it as a key.
  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {

    // long startTimeInMillis = System.currentTimeMillis();
    // List<Restaurant> restaurants = new ArrayList<>();

    // ModelMapper modelMapper = modelMapperProvider.get();
      //CHECKSTYLE:OFF
      // List<RestaurantEntity> restaurantEntities = restaurantRepository.findAll();
      // for (RestaurantEntity restaurantEntity : restaurantEntities) {
      //   if(isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude, servingRadiusInKms)) {
      //     restaurants.add(modelMapper.map(restaurantEntity, Restaurant.class));
      //   }
      // }
      // CHECKSTYLE:ON

      // long endTimeInMillis = System.currentTimeMillis();
      // System.out.println("Time taken by function at Repository is : " +(endTimeInMillis-startTimeInMillis));
      
      List<Restaurant> restaurants = new ArrayList<>();
      String redisKey = GeoHash.geoHashStringWithCharacterPrecision(latitude, longitude, 7);
  
      if (redisConfiguration.isCacheAvailable()) {
        try (Jedis jedis = redisConfiguration.getJedisPool().getResource()) {
          // Step 2: Check if data is present in the cache
          String cachedRestaurantsJson = jedis.get(redisKey);
  
          if (cachedRestaurantsJson != null) {
            ObjectMapper objectMapper = new ObjectMapper();
            restaurants = objectMapper.readValue(cachedRestaurantsJson, new TypeReference<List<Restaurant>>() {});
            return restaurants;
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
  
      // Step 3: Fetch data from the database if not present in the cache
      if (restaurants.isEmpty()) {
        List<RestaurantEntity> restaurantEntities = restaurantRepository.findAll();
        for (RestaurantEntity restaurantEntity : restaurantEntities) {
          if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude, servingRadiusInKms)) {
            ModelMapper modelMapper = modelMapperProvider.get();
            restaurants.add(modelMapper.map(restaurantEntity, Restaurant.class));
          }
        }
  
        // Step 4: Save the fetched data in the cache
        if (redisConfiguration.isCacheAvailable()) {
          try (Jedis jedis = redisConfiguration.getJedisPool().getResource()) {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonString = objectMapper.writeValueAsString(restaurants);
            jedis.setex(redisKey, RedisConfiguration.REDIS_ENTRY_EXPIRY_IN_SECONDS, jsonString);
          } catch (JsonProcessingException e) {
            e.printStackTrace();
          }
        }
      }
  
      // Step 5: Return the data
      return restaurants;
  }


  // public List<Restaurant> findAllRestaurantsCloseBy(Double latitude, Double longitude, LocalTime currentTime, Double servingRadiusInKms) {
  //   List<Restaurant> restaurants = null;
  //   return restaurants;
  // }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose names have an exact or partial match with the search query.
  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    Set<Restaurant> restaurantsByNameLHS = new LinkedHashSet<>();    
    List<RestaurantEntity> restaurantEntities = restaurantRepository.findRestaurantsByNameExact(searchString).get();
        for (RestaurantEntity restaurantEntity : restaurantEntities) {
          if(isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude, servingRadiusInKms)) {
            ModelMapper modelMapper = modelMapperProvider.get();
            restaurantsByNameLHS.add(modelMapper.map(restaurantEntity, Restaurant.class));
          }
        }
        List<Restaurant> restaurants = findAllRestaurantsCloseBy(latitude, longitude, currentTime, servingRadiusInKms);
        // List<Restaurant> restaurantsByNameList = restaurants.stream().filter(r->r.getName().equalsIgnoreCase(searchString)).collect(Collectors.toList());
        restaurantsByNameLHS.addAll(restaurants.stream().filter(r->r.getName().contains(searchString)).collect(Collectors.toSet()));
        List<Restaurant> restaurantsByNameList = new ArrayList<>(restaurantsByNameLHS);
    return restaurantsByNameList;
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose attributes (cuisines) intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByAttributes(Double latitude, Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
      List<Restaurant> restaurants = findAllRestaurantsCloseBy(latitude, longitude, currentTime, servingRadiusInKms);
      List<Restaurant> restaurantsByNameAttributes = restaurants.stream().filter(r->r.getAttributes().contains(searchString)).collect(Collectors.toList());
    return restaurantsByNameAttributes;
  }



  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose names form a complete or partial match
  // with the search query.

  @Override
  public List<Restaurant> findRestaurantsByItemName(Double latitude, Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
      List<Restaurant> restaurants = findAllRestaurantsCloseBy(latitude, longitude, currentTime, servingRadiusInKms);
      List<Restaurant> restaurantsByByItemName = new ArrayList<>();
      for (Restaurant r : restaurants) {
        List<String> itemNames = menuRepository.findMenuByRestaurantId(r.getRestaurantId()).get().getItems().stream().map(i->i.getName()).collect(Collectors.toList());
        for (String iname : itemNames) {
          if(iname.equalsIgnoreCase(searchString)) {
            restaurantsByByItemName.add(r);
            continue;
          }
        }
      }
      for (Restaurant r : restaurants) {
        List<String> itemNames = menuRepository.findMenuByRestaurantId(r.getRestaurantId()).get().getItems().stream().map(i->i.getName()).collect(Collectors.toList());
        for (String iname : itemNames) {
          if(iname.contains(searchString)) {
            restaurantsByByItemName.add(r);
            continue;
          }
        }
      }
    return restaurantsByByItemName;
  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose attributes intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
      List<Restaurant> restaurants = findAllRestaurantsCloseBy(latitude, longitude, currentTime, servingRadiusInKms);
      List<Restaurant> restaurantsByByItemName = new ArrayList<>();
      for (Restaurant r : restaurants) {
        List<Item> items = menuRepository.findMenuByRestaurantId(r.getRestaurantId()).get().getItems();
        Set<String> attributes = new HashSet<>();
        for(Item i : items) {
          attributes.addAll(i.getAttributes());
        }
        for (String a : attributes) {
          if(a.equalsIgnoreCase(searchString)) {
            restaurantsByByItemName.add(r);
            continue;
          }
        }
      }
    return restaurantsByByItemName;
  }


  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * @return boolean True if restaurant falls within serving radius and is open, false otherwise
   */
  private boolean isRestaurantCloseByAndOpen(RestaurantEntity restaurantEntity,
      LocalTime currentTime, Double latitude, Double longitude, Double servingRadiusInKms) {
    if (isOpenNow(currentTime, restaurantEntity)) {
      return GeoUtils.findDistanceInKm(latitude, longitude,
          restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
          < servingRadiusInKms;
    }

    return false;
  }

  //Async methods
  @Override
  public Future<List<Restaurant>> findRestaurantsByNameAsync(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    List<Restaurant> restaurantList = findRestaurantsByName(latitude, longitude, searchString, currentTime, servingRadiusInKms);
    return new AsyncResult<>(restaurantList);
  }

  @Override
  public Future<List<Restaurant>> findRestaurantsByAttributesAsync(Double latitude,
      Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    List<Restaurant> restaurantList = findRestaurantsByAttributes(latitude, longitude, searchString, currentTime, servingRadiusInKms);
    return new AsyncResult<>(restaurantList);
  }

  @Override
  public Future<List<Restaurant>> findRestaurantsByItemNameAsync(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    List<Restaurant> restaurantList = findRestaurantsByItemName(latitude, longitude, searchString, currentTime, servingRadiusInKms);
    return new AsyncResult<>(restaurantList);
  }

  @Override
  public Future<List<Restaurant>> findRestaurantsByItemAttributesAsync(Double latitude,
      Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    List<Restaurant> restaurantList = findRestaurantsByItemAttributes(latitude, longitude, searchString, currentTime, servingRadiusInKms);
    return new AsyncResult<>(restaurantList);
  }

}
