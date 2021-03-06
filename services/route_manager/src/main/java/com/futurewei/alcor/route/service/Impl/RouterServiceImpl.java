/*
MIT License
Copyright(c) 2020 Futurewei Cloud

    Permission is hereby granted,
    free of charge, to any person obtaining a copy of this software and associated documentation files(the "Software"), to deal in the Software without restriction,
    including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and / or sell copies of the Software, and to permit persons
    to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
    
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
    WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package com.futurewei.alcor.route.service.Impl;

import com.futurewei.alcor.common.db.CacheException;
import com.futurewei.alcor.common.enumClass.OperationType;
import com.futurewei.alcor.common.enumClass.RouteTableType;
import com.futurewei.alcor.common.enumClass.VpcRouteTarget;
import com.futurewei.alcor.common.exception.DatabasePersistenceException;
import com.futurewei.alcor.common.exception.ResourceNotFoundException;
import com.futurewei.alcor.common.exception.ResourcePersistenceException;
import com.futurewei.alcor.common.logging.Logger;
import com.futurewei.alcor.common.logging.LoggerFactory;
import com.futurewei.alcor.route.entity.RouteConstant;
import com.futurewei.alcor.route.exception.*;
import com.futurewei.alcor.route.service.*;
import com.futurewei.alcor.web.entity.route.*;
import com.futurewei.alcor.web.entity.subnet.HostRoute;
import com.futurewei.alcor.web.entity.subnet.SubnetEntity;
import com.futurewei.alcor.web.entity.subnet.SubnetWebJson;
import com.futurewei.alcor.web.entity.subnet.SubnetsWebJson;
import com.futurewei.alcor.web.entity.vpc.VpcEntity;
import com.futurewei.alcor.web.entity.vpc.VpcWebJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RouterServiceImpl implements RouterService {

    private Logger logger = LoggerFactory.getLogger();

    @Autowired
    private RouterDatabaseService routerDatabaseService;

    @Autowired
    private VpcRouterToVpcService vpcRouterToVpcService;

    @Autowired
    private VpcRouterToSubnetService vpcRouterToSubnetService;

    @Autowired
    private RouteTableDatabaseService routeTableDatabaseService;

    @Autowired
    private RouteEntryDatabaseService routeEntryDatabaseService;


    @Override
    public Router getVpcRouter(String projectId, String vpcId) throws CanNotFindVpc, CacheException, OwnMultipleVpcRouterException, CanNotFindRouter {
        // If VPC already has a router, return the router state
        Map<String, Router> routerMap = null;
        Map<String, Object[]> queryParams = new HashMap<>();
        Object[] values = new Object[1];
        values[0] = vpcId;
        queryParams.put("owner", values);

        routerMap = this.routerDatabaseService.getAllRouters(queryParams);

        if (routerMap == null || routerMap.size() == 0) {
            throw new CanNotFindRouter();
        }

        if (routerMap.size() > 1) {
            throw new OwnMultipleVpcRouterException();
        }

        Router router = routerMap.values().stream().findFirst().get();

        return router;
    }

    @Override
    public Router createVpcRouter(String projectId, String vpcId) throws CanNotFindVpc, DatabasePersistenceException, VpcRouterAlreadyExist {
        VpcWebJson vpcResponse = this.vpcRouterToVpcService.getVpcWebJson(projectId, vpcId);
        VpcEntity vpcEntity = vpcResponse.getNetwork();

        if (vpcEntity.getRouter() != null)
        {
            throw new VpcRouterAlreadyExist();
        }

        // If VPC doesn’t have a router, create a new router, create a VPC routing table and pump-in the VPC default routing rules
        return createDefaultVpcRouter(projectId, vpcEntity);
    }

    @Override
    public Router createDefaultVpcRouter(String projectId, VpcEntity vpcEntity) throws DatabasePersistenceException {
        String routerId = UUID.randomUUID().toString();
        String routeTableId = UUID.randomUUID().toString();
        String routeEntryId = UUID.randomUUID().toString();
        String owner = vpcEntity.getId();
        String destination = vpcEntity.getCidr();
        List<RouteTable> vpcRouteTables = new ArrayList<>();
        List<String> ports = new ArrayList<>();
        List<RouteEntry> routeEntities = new ArrayList<>();

        // create a VPC routing table and pump-in the VPC default routing rules
        RouteEntry routeEntry = new RouteEntry(projectId, routeEntryId, "default_vpc_routeEntry", "", destination, VpcRouteTarget.LOCAL.getVpcRouteTarget(), RouteConstant.DEFAULT_PRIORITY, routeTableId, null);
        routeEntities.add(routeEntry);
        this.routeEntryDatabaseService.addRouteEntry(routeEntry);

        RouteTable routeTable = new RouteTable(projectId, routeTableId, "default_vpc_routeTable", "", routeEntities, RouteTableType.VPC.getRouteTableType(), owner);
        vpcRouteTables.add(routeTable);
        this.routeTableDatabaseService.addRouteTable(routeTable);

        Router router = new Router(projectId, routerId, "default_vpc_router", "",
                null, vpcRouteTables, owner, ports, projectId, true, null, null, routeTableId);
        this.routerDatabaseService.addRouter(router);

        return router;
    }

    @Override
    public String deleteVpcRouter(String projectId, String vpcId) throws Exception {
        Router router = getVpcRouter(projectId, vpcId);
        if (router == null) {
            return null;
        }

        // check if there is any subnet exists in the VPC
        List<RouteTable> vpcRouteTable = router.getVpcRouteTables();
        SubnetsWebJson subnetsWebJson = this.vpcRouterToSubnetService.getSubnetsByVpcId(projectId, vpcId);
        if (subnetsWebJson != null) {
            ArrayList<SubnetEntity> subnets = subnetsWebJson.getSubnets();
            if (subnets != null && subnets.size() > 0) {
                throw new VpcNonEmptyException();
            }
        }

        // delete router and route tables
        this.routerDatabaseService.deleteRouter(router.getId());
        for (RouteTable routeTable : vpcRouteTable) {
            String routeTableType = routeTable.getRouteTableType();
            if (RouteTableType.VPC.getRouteTableType().equals(routeTableType)) {
                this.routeTableDatabaseService.deleteRouteTable(routeTable.getId());
            }
        }

        return router.getId();
    }

    @Override
    public RouteTable getVpcRouteTable(String projectId, String vpcId) throws DatabasePersistenceException, CanNotFindVpc, CacheException, OwnMultipleVpcRouterException, CanNotFindRouter {
        VpcWebJson vpcResponse = this.vpcRouterToVpcService.getVpcWebJson(projectId, vpcId);
        VpcEntity vpcEntity = vpcResponse.getNetwork();
        if (vpcEntity == null)
        {
            throw new CanNotFindVpc();
        }
        Router router = vpcEntity.getRouter();
        if (router == null) {
            throw new CanNotFindRouter();
        }

        // If VPC has a VPC routing table, return the routing table’s state
        List<RouteTable> vpcRouteTables = router.getVpcRouteTables();
        if (vpcRouteTables != null)
        {
            return vpcRouteTables.stream().filter(vpcRouteTable -> RouteTableType.VPC.getRouteTableType().equals(vpcRouteTable.getRouteTableType())).findFirst().orElse(null);
        }

        return null;
    }

    @Override
    public RouteTable createVpcRouteTable(String projectId, String vpcId) throws Exception {

        Router router = getVpcRouter(projectId, vpcId);
        if (router == null)
        {
            throw new CanNotFindRouter();
        }

        String defaultRouteTableId = router.getVpcDefaultRouteTableId();
        if (!defaultRouteTableId.isEmpty() && this.routeTableDatabaseService.getByRouteTableId(defaultRouteTableId) != null)
        {
            throw new RouteTableNotUnique();
        }

        return createDefaultVpcRouteTable(projectId, router);
    }

    @Override
    public RouteTable createDefaultVpcRouteTable(String projectId, Router router) throws DatabasePersistenceException {
        String routeTableId = UUID.randomUUID().toString();
        String routeEntryId = UUID.randomUUID().toString();
        String owner = router.getOwner();
        List<RouteEntry> routeEntities = new ArrayList<>();

        // create a VPC routing table and pump-in the VPC default routing rules
        RouteEntry routeEntry = new RouteEntry(projectId, routeEntryId, "default_vpc_routeEntry", "", null, VpcRouteTarget.LOCAL.getVpcRouteTarget(), RouteConstant.DEFAULT_PRIORITY, routeTableId, null);
        routeEntities.add(routeEntry);
        this.routeEntryDatabaseService.addRouteEntry(routeEntry);

        RouteTable routeTable = new RouteTable(projectId, routeTableId, "default_vpc_routeTable", "", routeEntities, RouteTableType.VPC.getRouteTableType(), owner);

        this.routeTableDatabaseService.addRouteTable(routeTable);

        router.setVpcDefaultRouteTableId(routeTableId);
        this.routerDatabaseService.addRouter(router);

        return routeTable;
    }

    @Override
    public RouteTable updateVpcRouteTable(String projectId, String vpcId, RouteTableWebJson resource) throws DatabasePersistenceException, CanNotFindVpc, CacheException, OwnMultipleVpcRouterException, ResourceNotFoundException, ResourcePersistenceException, CanNotFindRouter {
        RouteTable inRoutetable = resource.getRoutetable();

        // Get router
        Router router = getVpcRouter(projectId, vpcId);
        if (router == null)
        {
            throw new CanNotFindRouter();
        }

        // check if there is a vpc default routetable
        List<RouteTable> vpcRouteTables = router.getVpcRouteTables();
        String vpcDefaultRouteTableId = router.getVpcDefaultRouteTableId();
        RouteTable routeTable = this.routeTableDatabaseService.getByRouteTableId(vpcDefaultRouteTableId);

        if (routeTable == null) {
            String routeTableId = inRoutetable.getId();
            if (routeTableId == null) {
                routeTableId = UUID.randomUUID().toString();
                inRoutetable.setId(routeTableId);
            }
            inRoutetable.setRouteTableType(RouteTableType.VPC.getRouteTableType());
            vpcRouteTables.add(inRoutetable);
            router.setVpcRouteTables(vpcRouteTables);
            this.routerDatabaseService.addRouter(router);

            return inRoutetable;
        } else {
            List<RouteEntry> inRouteEntities = inRoutetable.getRouteEntities();

            routeTable.setRouteEntities(inRouteEntities);
            vpcRouteTables.add(routeTable);
            router.setVpcRouteTables(vpcRouteTables);
            this.routerDatabaseService.addRouter(router);

            return routeTable;
        }

    }

    @Override
    public List<RouteTable> getVpcRouteTables(String projectId, String vpcId) throws CanNotFindVpc, CanNotFindRouter {
        VpcWebJson vpcResponse = this.vpcRouterToVpcService.getVpcWebJson(projectId, vpcId);
        VpcEntity vpcEntity = vpcResponse.getNetwork();
        Router router = vpcEntity.getRouter();
        if (router == null) {
            throw new CanNotFindRouter();
        }
        return router.getVpcRouteTables();
    }

    @Override
    public RouteTable getSubnetRouteTable(String projectId, String subnetId) throws CacheException, CanNotFindRouteTableByOwner, OwnMultipleSubnetRouteTablesException {
        RouteTable routeTable = null;

        Map<String, RouteTable> routeTableMap = null;
        Map<String, Object[]> queryParams = new HashMap<>();
        Object[] values = new Object[1];
        values[0] = subnetId;
        queryParams.put("owner", values);

        routeTableMap = this.routeTableDatabaseService.getAllRouteTables(queryParams);

        if (routeTableMap == null || routeTableMap.size() == 0) {
            throw new CanNotFindRouteTableByOwner();
        }

        if (routeTableMap.size() > 1)
        {
            throw new OwnMultipleSubnetRouteTablesException();
        }

        return routeTableMap.values().stream().findFirst().get();
    }

    @Override
    public RouteTable updateSubnetRouteTable(String projectId, String subnetId, UpdateRoutingRuleResponse resource) throws CacheException, DatabasePersistenceException, OwnMultipleSubnetRouteTablesException, CanNotFindRouteTableByOwner {
        InternalSubnetRoutingTable inRoutetable = resource.getInternalSubnetRoutingTable();
        List<HostRoute> inHostRoutes = resource.getHostRouteToSubnet();
        // Get or create a router for a Subnet
        RouteTable routeTable = getSubnetRouteTable(projectId, subnetId);

        for (InternalRoutingRule inRule : inRoutetable.getRoutingRules()) {
            if (OperationType.CREATE.equals(inRule.getOperationType())) {
                RouteEntry newRoute = new RouteEntry(projectId, inRule.getId(), inRule.getName(), null,
                        inRule.getDestination(), null, inRule.getPriority(), null, inRule.getNextHopIp());
                routeTable.getRouteEntities().add(newRoute);
            } else {
                RouteEntry route = routeTable.getRouteEntities().stream().filter(e -> e.getId().equals(inRule.getId())).findFirst().orElse(null);
                if (route != null) {
                    if (OperationType.UPDATE.equals(inRule.getOperationType())) {
                        RouteEntry uRoute = new RouteEntry(projectId, route.getId(), inRule.getName(), null,
                                inRule.getDestination(), null, inRule.getPriority(), null, inRule.getNextHopIp());
                        // delete old route rule
                        for (RouteEntry routeEntry : routeTable.getRouteEntities()) {
                            if (routeEntry.getId().equals(route.getId())) {
                                routeTable.getRouteEntities().remove(routeEntry);
                                break;
                            }
                        }
                        routeTable.getRouteEntities().add(uRoute);
                    } else if (OperationType.DELETE.equals(inRule.getOperationType())) {
                        routeTable.getRouteEntities().remove(route);
                    }
                }
            }
        }
        if (routeTable != null) {
            this.routeTableDatabaseService.addRouteTable(routeTable);
            // TODO: notify Subnet Manager to update L3 neighbor for all ports in the same subnet
        }

        return routeTable;
    }

    @Override
    public String deleteSubnetRouteTable(String projectId, String subnetId) throws Exception {
        RouteTable routeTable = null;

        // Get or create a router for a Subnet
        routeTable = getSubnetRouteTable(projectId, subnetId);
        if (routeTable == null) {
            return null;
        }

        String routeTableId = routeTable.getId();

        this.routeTableDatabaseService.deleteRouteTable(routeTableId);

        return routeTableId;
    }

    @Override
    public RouteTable createSubnetRouteTable(String projectId, String subnetId, RouteTableWebJson resource, List<RouteEntry> routes) throws DatabasePersistenceException {

        // configure a new route table
        RouteTable routeTable = new RouteTable();
        String id = UUID.randomUUID().toString();
        routeTable.setId(id);
        routeTable.setDescription("");
        routeTable.setName("subnet-" + id + "-routetable");
        routeTable.setProjectId(projectId);
        routeTable.setRouteTableType(resource.getRoutetable().getRouteTableType());
        routeTable.setOwner(subnetId);

        routeTable.setRouteEntities(routes);

        this.routeTableDatabaseService.addRouteTable(routeTable);

        return routeTable;
    }


}
