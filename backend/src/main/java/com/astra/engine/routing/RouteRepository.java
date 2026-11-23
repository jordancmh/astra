package com.astra.engine.routing;

import org.locationtech.jts.geom.Geometry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, String> {

    @Query("SELECT r FROM Route r WHERE intersects(r.pathGeometry, :geom) = true")
    List<Route> findRoutesIntersecting(@Param("geom") Geometry geom);
}
