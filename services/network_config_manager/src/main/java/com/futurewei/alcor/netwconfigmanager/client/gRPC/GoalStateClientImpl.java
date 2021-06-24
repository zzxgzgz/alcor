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
package com.futurewei.alcor.netwconfigmanager.client.gRPC;

import com.futurewei.alcor.common.logging.Logger;
import com.futurewei.alcor.common.logging.LoggerFactory;
import com.futurewei.alcor.common.stats.DurationStatistics;
import com.futurewei.alcor.netwconfigmanager.client.GoalStateClient;
import com.futurewei.alcor.netwconfigmanager.config.Config;
import com.futurewei.alcor.netwconfigmanager.entity.HostGoalState;
import com.futurewei.alcor.schema.GoalStateProvisionerGrpc;
import com.futurewei.alcor.schema.Goalstate;
import com.futurewei.alcor.schema.Goalstateprovisioner;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Service("grpcGoalStateClient")
public class GoalStateClientImpl implements GoalStateClient {

//    private static GoalStateClientImpl instance = null;

    private static final Logger logger = LoggerFactory.getLogger();

    private int hostAgentPort;

    private final ExecutorService executor;

//    private SortedMap<String, GrpcChannelStub> hostIpGrpcChannelStubMap;
//
//
//    public static GoalStateClientImpl getInstance(){
//        if (instance == null){
//            instance = new GoalStateClientImpl();
//        }
//        return instance;
//    }

    //    @Autowired
    public GoalStateClientImpl() {
//        this.grpcPort = globalConfig.targetHostPort;
//        this.executor = new ThreadPoolExecutor(globalConfig.grpcMinThreads,
//                globalConfig.grpcMaxThreads,
//                50,
//                TimeUnit.SECONDS,
//                new LinkedBlockingDeque<>(),
//                new DefaultThreadFactory(globalConfig.grpThreadsName));
        this.hostAgentPort = 50001;
        this.executor = new ThreadPoolExecutor(100,
                200,
                50,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(),
                new DefaultThreadFactory("grpc-thread-pool"));

        //TODO: Setup a connection pool. one ACA, one client.
//        this.hostIpGrpcChannelStubMap = new TreeMap<>();
    }

    @Override
    @DurationStatistics
    public List<String> sendGoalStates(Map<String, HostGoalState> hostGoalStates) throws Exception {
        List<Future<HostGoalState>>
                futures = new ArrayList<>(hostGoalStates.size());

        for (HostGoalState hostGoalState : hostGoalStates.values()) {
            Future<HostGoalState> future =
                    executor.submit(() -> {
                        try {
                            doSendGoalState(hostGoalState);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            return hostGoalState;
                        }

                        return new HostGoalState();
                    });

            futures.add(future);
        }

        //Handle all failed hosts
        return futures.parallelStream().filter(Objects::nonNull).map(future -> {
            try {
                return future.get().getHostIp();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            return null;
        }).collect(Collectors.toList());
    }

//    private GrpcChannelStub getOrCreateGrpcChannel(String hostIp){
//        if(!this.hostIpGrpcChannelStubMap.containsKey(hostIp)){
//            ManagedChannel channel = ManagedChannelBuilder.forAddress(hostIp, this.hostAgentPort)
//                    .usePlaintext()
//                    .keepAliveWithoutCalls(true)
//                    .keepAliveTime(Long.MAX_VALUE, TimeUnit.SECONDS)
//                    .build();
//            GoalStateProvisionerGrpc.GoalStateProvisionerStub asyncStub = GoalStateProvisionerGrpc.newStub(channel);
//            this.hostIpGrpcChannelStubMap.put(hostIp, new GrpcChannelStub(channel, asyncStub));
//            logger.log(Level.INFO, "[getOrCreateGrpcChannel] Created a channel and stub to host IP: " + hostIp);
//        }
//        ManagedChannel chan = this.hostIpGrpcChannelStubMap.get(hostIp).channel;
//        //checks the channel status, reconnects if the channel is IDLE
//
//        ConnectivityState channelState =chan.getState(true);
//        if (channelState != ConnectivityState.READY && channelState != ConnectivityState.CONNECTING && channelState != ConnectivityState.IDLE){
//            // if the state is not good, we can always create another channel to replace the current one
//            ManagedChannel channel = ManagedChannelBuilder.forAddress(hostIp, this.hostAgentPort)
//                    .usePlaintext()
//                    .keepAliveWithoutCalls(true)
//                    .keepAliveTime(Long.MAX_VALUE, TimeUnit.SECONDS)
//                    .build();
//            GoalStateProvisionerGrpc.GoalStateProvisionerStub asyncStub = GoalStateProvisionerGrpc.newStub(channel);
//
//            this.hostIpGrpcChannelStubMap.put(hostIp, new GrpcChannelStub(channel, asyncStub));
//            logger.log(Level.INFO, "[getOrCreateGrpcChannel] Replaced a channel and stub to host IP: " + hostIp);
//        }
//        return this.hostIpGrpcChannelStubMap.get(hostIp);
//    }
//

    private void doSendGoalState(HostGoalState hostGoalState) throws InterruptedException {

        String hostIp = hostGoalState.getHostIp();
        logger.log(Level.INFO, "Setting up a channel to ACA on: " + hostIp);
        long start = System.currentTimeMillis();
        if(hostGoalState.getGoalState().getNeighborStatesCount() == 1 && hostIp.equals("10.213.43.92")){
            // If there's only one neighbor state and it is trying to send it to aca_node_one, the IP of which is now
            // hardcoded) this send goalstate action is probably caused by on-demand workflow, need to record when it
            // sends this goalState so what we can look into this and the ACA log to see how much time was spent.
            String neighbor_id = hostGoalState.getGoalState().getNeighborStatesMap().keySet().iterator().next();
            logger.log(Level.INFO, "Sending neighbor ID: " + neighbor_id +" at: " + start);
        }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(hostIp, this.hostAgentPort)
                .usePlaintext()
                .build();
//        GrpcChannelStub channelStub = getOrCreateGrpcChannel(hostIp);
        long chan_established = System.currentTimeMillis();
        logger.log(Level.INFO, "[doSendGoalState] Established channel, elapsed Time in milli seconds: "+ (chan_established-start));
//        GoalStateProvisionerGrpc.GoalStateProvisionerStub asyncStub = channelStub.stub;
        GoalStateProvisionerGrpc.GoalStateProvisionerStub asyncStub = GoalStateProvisionerGrpc.newStub(channel);
        long stub_established = System.currentTimeMillis();
        logger.log(Level.INFO, "[doSendGoalState] Established stub, elapsed Time after channel established in milli seconds: "+ (stub_established-chan_established));
        Map<String, List<Goalstateprovisioner.GoalStateOperationReply.GoalStateOperationStatus>> result = new HashMap<>();
        StreamObserver<Goalstateprovisioner.GoalStateOperationReply> responseObserver = new StreamObserver<>() {
            long on_next, on_completed;
            @Override
            public void onNext(Goalstateprovisioner.GoalStateOperationReply reply) {
                on_next = System.currentTimeMillis();
                logger.log(Level.INFO, "Receive response from ACA@" + hostIp + " | " + reply.toString() );
                result.put(hostIp, reply.getOperationStatusesList());
                logger.log(Level.INFO, "[doSendGoalState] Called onNext, elapsed Time after stub established in milli seconds: "+ (on_next-stub_established));
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "Receive error from ACA@" + hostIp + " |  " + t.getMessage() );
            }

            @Override
            public void onCompleted() {
                on_completed = System.currentTimeMillis();
                logger.log(Level.INFO, "Complete receiving message from ACA@" + hostIp);
                logger.log(Level.INFO, "[doSendGoalState] Called onComplete, elapsed Time after onNext in milli seconds: "+ (on_completed-on_next));
            }
        };

        StreamObserver<Goalstate.GoalStateV2> requestObserver = asyncStub.pushGoalStatesStream(responseObserver);
        try {
                Goalstate.GoalStateV2 goalState = hostGoalState.getGoalState();
                logger.log(Level.INFO, "Sending GS to Host " + hostIp + " as follows | " + goalState.toString());

                requestObserver.onNext(goalState);
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        logger.log(Level.INFO, "Sending GS to Host " + hostIp + " is completed");
        requestObserver.onCompleted();
//        shutdown(channel);
    }

    private void shutdown(ManagedChannel channel) {
        try {
            channel.shutdown().awaitTermination(Config.SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING,"Timed out forcefully shutting down connection: {}", e.getMessage());
        }
    }

    private class GrpcChannelStub{
        public ManagedChannel channel;
        public GoalStateProvisionerGrpc.GoalStateProvisionerStub stub;

        public GrpcChannelStub(ManagedChannel channel, GoalStateProvisionerGrpc.GoalStateProvisionerStub stub){
            this.channel = channel;
            this.stub = stub;
        }
    }

}
