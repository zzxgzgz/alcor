/*
This is the code for the test controller, for testing the reactions between the Network Configuration manager and
the ACA.

Params:
1. Number of ports to generate to each aca node
2. IP of aca_node_one
3. IP of aca_node_two
4. User name of aca_nodes
5. Password of aca_nodes
*/
package com.futurewei.alcor.pseudo_controller;
import com.futurewei.alcor.schema.*;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.InputStream;
import java.io.OutputStream;


public class pseudo_controller {

    static String aca_node_one_ip = "ip_one";
    static String aca_node_two_ip = "ip_two";
    static String user_name = "root";
    static String password = "abcdefg";
    static int ports_to_generate_on_each_aca_node = 1;
    static String vpc_id_1 = "2b08a5bc-b718-11ea-b3de-111111111112";
    static String port_id_1 = "11111111-b718-11ea-b3de-111111111112";
    static String port_id_2 = "13333333-b718-11ea-b3de-111111111114";
    static String subnet_id_1 = "27330ae4-b718-11ea-b3df-111111111113";
    public static void main(String[] args){
        System.out.println("Start of the test controller");
        if(args.length == 5){
            System.out.println("User passed in params and we need to read them.");
            ports_to_generate_on_each_aca_node = Integer.parseInt(args[0]);
            aca_node_one_ip = args[1];
            aca_node_two_ip = args[2];
            user_name = args[3];
            password = args[4];
        }

        System.out.println("aca_node_one_ip: " + aca_node_one_ip + "\naca_node_two_ip: " + aca_node_two_ip + "\nuser name: "+user_name+"\npassword: "+password);
//        execute_ssh_commands("docker run -itd --name test1 --net=none busybox sh", aca_node_one_ip, user_name, password);
//        execute_ssh_commands("ovs-docker add-port br-int eth0 test1 --ipaddress=10.0.0.2/16 --macaddress=6c:dd:ee:00:00:02", aca_node_one_ip, user_name, password);
//
//        execute_ssh_commands("docker ps", aca_node_one_ip, user_name, password);
//        execute_ssh_commands("docker run -itd --name test2 --net=none busybox sh", aca_node_two_ip, user_name, password);
//        execute_ssh_commands("ovs-docker add-port br-int eth0 test2 --ipaddress=10.0.0.3/16 --macaddress=6c:dd:ee:00:00:03", aca_node_two_ip, user_name, password);
//
//        execute_ssh_commands("docker ps", aca_node_two_ip, user_name, password);


        System.out.println("Containers setup done, now we gotta construct the GoalStateV2");

        System.out.println("Trying to build the goalstatev2");


        Goalstate.GoalStateV2 GoalState_builder = Goalstate.GoalStateV2.newBuilder().build();

        // start of setting up port 1 on aca node 1
        Port.PortState new_port_states = Port.PortState.newBuilder().build();

        new_port_states.newBuilderForType().setOperationType(Common.OperationType.CREATE);

        // fill in port state structs for port 1
        Port.PortConfiguration Port_Configuration_builder = new_port_states.getConfiguration();
        Port_Configuration_builder.toBuilder().setRevisionNumber(2);
        Port_Configuration_builder.toBuilder().setUpdateType(Common.UpdateType.FULL);
        Port_Configuration_builder.toBuilder().setId(port_id_1);
        Port_Configuration_builder.toBuilder().setVpcId(vpc_id_1);
        Port_Configuration_builder.toBuilder().setName(("tap" + port_id_1).substring(0, 14));
        Port_Configuration_builder.toBuilder().setMacAddress("6c:dd:ee:00:00:02");
        Port_Configuration_builder.toBuilder().setAdminStateUp(true);

        Port_Configuration_builder.toBuilder().addFixedIpsBuilder();
        Port_Configuration_builder.toBuilder().getFixedIpsBuilderList().get(0).setSubnetId(subnet_id_1);
        Port_Configuration_builder.toBuilder().getFixedIpsBuilderList().get(0).setIpAddress("10.0.0.2");

        Port_Configuration_builder.toBuilder().addSecurityGroupIdsBuilder();
        Port_Configuration_builder.toBuilder().getSecurityGroupIdsBuilder(0).setId("2");

        // put the new port states of port 1 into the goalstatev2
        GoalState_builder.getPortStatesMap().put("10.213.43.92-2",new_port_states);

        // fill in subnet state structs
        Subnet.SubnetState new_subnet_states = Subnet.SubnetState.newBuilder().build();

        new_subnet_states.toBuilder().setOperationType(Common.OperationType.INFO);
        Subnet.SubnetConfiguration Subnetcofiguration_builder = new_subnet_states.getConfiguration();
        Subnetcofiguration_builder.toBuilder().setRevisionNumber(2);
        Subnetcofiguration_builder.toBuilder().setVpcId(vpc_id_1);
        Subnetcofiguration_builder.toBuilder().setId(subnet_id_1);
        Subnetcofiguration_builder.toBuilder().setCidr("10.0.0.0/24");
        Subnetcofiguration_builder.toBuilder().setTunnelId(21);

        // put the new subnet state of subnet 1 into the goalstatev2

        GoalState_builder.getSubnetStatesMap().put("10.213.43.92-1", new_subnet_states);
        GoalState_builder.getSubnetStatesMap().put("10.213.43.93-1", new_subnet_states);

        // add a new neighbor state with CREATE
        Neighbor.NeighborState new_neighborState = Neighbor.NeighborState.newBuilder().build();
        new_neighborState.toBuilder().setOperationType(Common.OperationType.CREATE);

        // fill in neighbor state structs of port 3
        Neighbor.NeighborConfiguration NeighborConfiguration_builder = new_neighborState.getConfiguration();
        NeighborConfiguration_builder.toBuilder().setRevisionNumber(2);
        NeighborConfiguration_builder.toBuilder().setVpcId(vpc_id_1);
        NeighborConfiguration_builder.toBuilder().setId(port_id_2);
        NeighborConfiguration_builder.toBuilder().setMacAddress("6c:dd:ee:00:00:03");
        NeighborConfiguration_builder.toBuilder().setHostIpAddress(aca_node_two_ip);

        NeighborConfiguration_builder.toBuilder().addFixedIpsBuilder();
        NeighborConfiguration_builder.toBuilder().getFixedIpsBuilder(0).setNeighborType(Neighbor.NeighborType.L2);
        NeighborConfiguration_builder.toBuilder().getFixedIpsBuilder(0).setSubnetId(subnet_id_1);
        NeighborConfiguration_builder.toBuilder().getFixedIpsBuilder(0).setIpAddress("10.0.0.3");

        GoalState_builder.getNeighborStatesMap().put("10.213.43.92-3", new_neighborState);

        // end of setting up port 1 on aca node 1

        // start of setting up port 2 on aca node 2

        Port.PortState new_port_states_2 = Port.PortState.newBuilder().build();

        new_port_states.newBuilderForType().setOperationType(Common.OperationType.CREATE);

        // fill in port state structs for port 1
        Port.PortConfiguration Port_Configuration_builder_2 = new_port_states_2.getConfiguration();
        Port_Configuration_builder_2.toBuilder().setRevisionNumber(2);
        Port_Configuration_builder_2.toBuilder().setUpdateType(Common.UpdateType.FULL);
        Port_Configuration_builder_2.toBuilder().setId(port_id_2);
        Port_Configuration_builder_2.toBuilder().setVpcId(vpc_id_1);
        Port_Configuration_builder_2.toBuilder().setName(("tap" + port_id_2).substring(0, 14));
        Port_Configuration_builder_2.toBuilder().setMacAddress("6c:dd:ee:00:00:03");
        Port_Configuration_builder_2.toBuilder().setAdminStateUp(true);

        Port_Configuration_builder_2.toBuilder().addFixedIpsBuilder();
        Port_Configuration_builder_2.toBuilder().getFixedIpsBuilderList().get(0).setSubnetId(subnet_id_1);
        Port_Configuration_builder_2.toBuilder().getFixedIpsBuilderList().get(0).setIpAddress("10.0.0.3");

        Port_Configuration_builder_2.toBuilder().addSecurityGroupIdsBuilder();
        Port_Configuration_builder_2.toBuilder().getSecurityGroupIdsBuilder(0).setId("2");

        // put the new port states of port 1 into the goalstatev2
        GoalState_builder.getPortStatesMap().put("10.213.43.93-2",new_port_states_2);


        // add a new neighbor state with CREATE
        Neighbor.NeighborState new_neighborState_2 = Neighbor.NeighborState.newBuilder().build();
        new_neighborState_2.toBuilder().setOperationType(Common.OperationType.CREATE);

        // fill in neighbor state structs of port 1
        Neighbor.NeighborConfiguration NeighborConfiguration_builder_2 = new_neighborState.getConfiguration();
        NeighborConfiguration_builder_2.toBuilder().setRevisionNumber(2);
        NeighborConfiguration_builder_2.toBuilder().setVpcId(vpc_id_1);
        NeighborConfiguration_builder_2.toBuilder().setId(port_id_1);
        NeighborConfiguration_builder_2.toBuilder().setMacAddress("6c:dd:ee:00:00:02");
        NeighborConfiguration_builder_2.toBuilder().setHostIpAddress(aca_node_one_ip);

        NeighborConfiguration_builder_2.toBuilder().addFixedIpsBuilder();
        NeighborConfiguration_builder_2.toBuilder().getFixedIpsBuilder(0).setNeighborType(Neighbor.NeighborType.L2);
        NeighborConfiguration_builder_2.toBuilder().getFixedIpsBuilder(0).setSubnetId(subnet_id_1);
        NeighborConfiguration_builder_2.toBuilder().getFixedIpsBuilder(0).setIpAddress("10.0.0.2");

        GoalState_builder.getNeighborStatesMap().put("10.213.43.93-3", new_neighborState_2);

        // end of setting up port 2 on aca node 2
        System.out.println("Built GoalState successfully");

        System.out.println("End of the test controller");
    }

    public static void execute_ssh_commands(String command, String host_ip, String host_user_name, String host_password){
        try{

            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            JSch jsch = new JSch();
            Session session=jsch.getSession(host_user_name, host_ip, 22);
            session.setPassword(host_password);
            session.setConfig(config);
            session.connect();
            System.out.println("Connected");

            Channel channel=session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);
            channel.setInputStream(null);
            ((ChannelExec)channel).setErrStream(System.err);

            InputStream in=channel.getInputStream();
            channel.connect();
            byte[] tmp=new byte[1024];
            while(true){
                while(in.available()>0){
                    int i=in.read(tmp, 0, 1024);
                    if(i<0)break;
                    System.out.print(new String(tmp, 0, i));
                }
                if(channel.isClosed()){
                    System.out.println("exit-status: "+channel.getExitStatus());
                    break;
                }
                try{Thread.sleep(1000);}catch(Exception ee){}
            }
            channel.disconnect();
            session.disconnect();
            System.out.println("DONE");
        }catch(Exception e){
            System.err.println("Got this error: " + e.getMessage());
            e.printStackTrace();
        }

    }
}
