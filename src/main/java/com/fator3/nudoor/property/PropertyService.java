package com.fator3.nudoor.property;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fator3.nudoor.clients.TomtomClient;
import com.fator3.nudoor.geometry.GeometryUtils;
import com.fator3.nudoor.models.GeolocationResponse;
import com.fator3.nudoor.models.LatLng;
import com.fator3.nudoor.models.Leg;
import com.fator3.nudoor.models.ReachableRangeResponse;
import com.fator3.nudoor.models.RouteResponse;
import com.fator3.nudoor.models.SearchParamsDTO;
import com.fator3.nudoor.models.TimedLatLng;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;

@Component
public class PropertyService {

    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private TomtomClient tomtomClient;

    private WKTReader reader = new WKTReader();

    public void save(final Property property) {
        propertyRepository.save(property);
    }

    public List<Property> findAll() {
        return propertyRepository.findAll();
    }

    public List<TimedLatLng> findDistanceInSeconds(final List<TimedLatLng> orderedReferences) {
        final RouteResponse routeResponse = tomtomClient.getRoute(orderedReferences);
        final List<Leg> legs = Iterables.getOnlyElement(routeResponse.getRoutes()).getLegs();

        for (int i = 1; i < orderedReferences.size(); i++) {
            final TimedLatLng location = orderedReferences.get(i);
            final Integer secondsToArrive = legs.get(i - 1).getSummary().getTravelTimeInSeconds();
            location.setSecondsToArrive(secondsToArrive);
        }

        return orderedReferences;
    }

    public List<TimedLatLng> findDistanceInSeconds(List<TimedLatLng> orderedReferences,
            final String address) {

        final TimedLatLng addressLocation = Iterables
                .getOnlyElement(geolocationList(Collections.singletonList(address)));

        orderedReferences.add(addressLocation);
        orderedReferences = Lists.reverse(orderedReferences);
        final RouteResponse routeResponse = tomtomClient.getRoute(orderedReferences);
        final List<Leg> legs = Iterables.getOnlyElement(routeResponse.getRoutes()).getLegs();

        for (int i = 1; i < orderedReferences.size(); i++) {
            final TimedLatLng location = orderedReferences.get(i);
            final Integer secondsToArrive = legs.get(i - 1).getSummary().getTravelTimeInSeconds();
            location.setSecondsToArrive(secondsToArrive);
        }

        return orderedReferences;
    }

    public List<Property> findWithinRange(final SearchParamsDTO searchParams) {
    	List<String> references = searchParams.getReferences();
    	List<Integer> referencesMinutes = searchParams.getReferencesMinutes();
        List<Property> properties = propertyRepository.findAll();
        final List<TimedLatLng> referencesLatLng = geolocationList(references);
        
        
        for(int i = 0 ; i< referencesLatLng.size(); i++){
        	TimedLatLng t = referencesLatLng.get(i);
        	Integer minutes = referencesMinutes.get(i);
            Point point = GeometryUtils.createPoint(t.getLatitude(), t.getLongitude(), reader);
            ReachableRangeResponse reachableResponse = tomtomClient.getPolygonReachable(point, minutes);
            
            List<LatLng> boundaries = reachableResponse.getReachableRange().getBoundary();
            boundaries.add(boundaries.get(0));

            Polygon polygon = GeometryUtils.createPolygon(boundaries, reader);

            
            properties = properties.stream().filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                    .filter(p -> polygon.contains(GeometryUtils.createPoint(p.getLatitude().doubleValue(), p.getLongitude().doubleValue(), reader)))
                    .collect(Collectors.toList());
        }
        return properties;
    }

    public List<TimedLatLng> geolocationList(final List<String> references) {
        final List<TimedLatLng> results = Lists.newArrayList();
        for (String reference : references) {
            final GeolocationResponse result = tomtomClient.getLatLng(reference);
            final double latitude = result.getResults().get(0).getPosition().getLatitude();
            final double longitude = result.getResults().get(0).getPosition().getLongitude();
            results.add(TimedLatLng.of(latitude, longitude));
        }
        return results;
    }
    
    public List<Property> listNRandom(final Integer limit){
    	return propertyRepository.listNRandom(limit);
    }

}