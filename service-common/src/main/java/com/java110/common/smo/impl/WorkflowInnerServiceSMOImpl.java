package com.java110.common.smo.impl;


import com.java110.common.dao.IWorkflowServiceDao;
import com.java110.core.base.smo.BaseServiceSMO;
import com.java110.core.smo.common.IWorkflowInnerServiceSMO;
import com.java110.core.smo.user.IUserInnerServiceSMO;
import com.java110.dto.PageDto;
import com.java110.dto.user.UserDto;
import com.java110.dto.workflow.WorkflowDto;
import com.java110.dto.workflow.WorkflowStepDto;
import com.java110.dto.workflow.WorkflowStepStaffDto;
import com.java110.utils.util.Base64Convert;
import com.java110.utils.util.BeanConvertUtil;
import com.java110.utils.util.StringUtil;
import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.*;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngines;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.repository.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName FloorInnerServiceSMOImpl
 * @Description 工作流内部服务实现类
 * @Author wuxw
 * @Date 2019/4/24 9:20
 * @Version 1.0
 * add by wuxw 2019/4/24
 **/
@RestController
public class WorkflowInnerServiceSMOImpl extends BaseServiceSMO implements IWorkflowInnerServiceSMO {
    private static final Logger logger = LoggerFactory.getLogger(BaseServiceSMO.class);

    @Autowired
    private IWorkflowServiceDao workflowServiceDaoImpl;

    @Autowired
    private IUserInnerServiceSMO userInnerServiceSMOImpl;

    @Autowired
    private RuntimeService runtimeService;

    @Override
    public List<WorkflowDto> queryWorkflows(@RequestBody WorkflowDto workflowDto) {

        //校验是否传了 分页信息

        int page = workflowDto.getPage();

        if (page != PageDto.DEFAULT_PAGE) {
            workflowDto.setPage((page - 1) * workflowDto.getRow());
        }

        List<WorkflowDto> workflows = BeanConvertUtil.covertBeanList(workflowServiceDaoImpl.getWorkflowInfo(BeanConvertUtil.beanCovertMap(workflowDto)), WorkflowDto.class);

        if (workflows == null || workflows.size() == 0) {
            return workflows;
        }

        String[] userIds = getUserIds(workflows);
        //根据 userId 查询用户信息
        List<UserDto> users = userInnerServiceSMOImpl.getUserInfo(userIds);

        for (WorkflowDto workflow : workflows) {
            refreshWorkflow(workflow, users);
        }
        return workflows;
    }

    /**
     * 从用户列表中查询用户，将用户中的信息 刷新到 floor对象中
     *
     * @param workflow 小区工作流信息
     * @param users    用户列表
     */
    private void refreshWorkflow(WorkflowDto workflow, List<UserDto> users) {
        for (UserDto user : users) {
            if (workflow.getFlowId().equals(user.getUserId())) {
                BeanConvertUtil.covertBean(user, workflow);
            }
        }
    }

    /**
     * 获取批量userId
     *
     * @param workflows 小区楼信息
     * @return 批量userIds 信息
     */
    private String[] getUserIds(List<WorkflowDto> workflows) {
        List<String> userIds = new ArrayList<String>();
        for (WorkflowDto workflow : workflows) {
            userIds.add(workflow.getFlowId());
        }

        return userIds.toArray(new String[userIds.size()]);
    }

    public String getWorkflowImage(@RequestBody WorkflowDto workflowDto) {

        ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
        List<String> list = processEngine.getRepositoryService()//
                .getDeploymentResourceNames(workflowDto.getProcessDefinitionKey());
        String image = "";
        String resourceName = "";
        if (list != null && list.size() > 0) {
            for (String name : list) {
                if (name.indexOf(".png") >= 0) {
                    resourceName = name;
                }
            }
        }

        InputStream in = processEngine.getRepositoryService()
                .getResourceAsStream(workflowDto.getProcessDefinitionKey(), resourceName);
        try {
            image = Base64Convert.ioToBase64(in);
        } catch (IOException e) {
            logger.error("读取图片失败", e);
        }
        return image;
    }

    /**
     * @Date：2017/11/24
     * @Description：创建流程并部署
     */
    public WorkflowDto addFlowDeployment(@RequestBody WorkflowDto workflowDto) {

        ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
//
//        RepositoryService repositoryService  = processEngine.getRepositoryService();
//        repositoryService.deleteDeployment("1");
        try {
            // 1. 建立模型
            BpmnModel model = new BpmnModel();
            Process process = new Process();
            model.addProcess(process);
            process.setId(WorkflowDto.DEFAULT_PROCESS + workflowDto.getFlowId());
            process.setName(workflowDto.getFlowName());
            process.setDocumentation(workflowDto.getDescrible());
            //添加流程
            //开始节点
            process.addFlowElement(createStartEvent());
            List<WorkflowStepDto> workflowStepDtos = workflowDto.getWorkflowSteps();
            for (int i = 0; i < workflowStepDtos.size(); i++) {
                WorkflowStepDto step = workflowStepDtos.get(i);
                //判断是否会签
                if (WorkflowStepDto.TYPE_COUNTERSIGN.equals(step.getType())) {
                    //会签
                    //加入并行网关-分支
                    process.addFlowElement(createParallelGateway("parallelGateway-fork" + i, "parallelGateway-fork" + i));
                    //获取角色下所有用户
                    List<WorkflowStepStaffDto> userList = step.getWorkflowStepStaffs();
                    for (int u = 0; u < userList.size(); u++) {
                        //并行网关分支的审核节点
                        process.addFlowElement(createUserTask("userTask" + i + u, userList.get(u).getStaffName(), userList.get(u).getStaffId()));
                    }
                    //并行网关-汇聚
                    process.addFlowElement(createParallelGateway("parallelGateway-join" + i, "parallelGateway-join" + i));

                    process.addFlowElement(createUserTask("repulse" + i, "提交者", "${startUserId}"));

                } else {
                    //普通流转
                    //审核节点
                    process.addFlowElement(createGroupTask("task" + i, step.getWorkflowStepStaffs().get(0).getStaffName(), step.getWorkflowStepStaffs().get(0).getStaffId()));
                    //回退节点
                    process.addFlowElement(createUserTask("repulse" + i, "提交者", "${startUserId}"));
                }
            }
            //结束节点
            process.addFlowElement(createEndEvent());

            //连线
            for (int y = 0; y < workflowStepDtos.size(); y++) {
                WorkflowStepDto step = workflowStepDtos.get(y);
                //是否会签
                if (WorkflowStepDto.TYPE_COUNTERSIGN.equals(step.getType())) {
                    //会签
                    //判断是否第一个节点
                    if (y == 0) {
                        //开始节点和并行网关-分支连线
                        process.addFlowElement(createSequenceFlow("startEvent", "parallelGateway-fork" + y, "startEvent-parallelGateway-fork" + y, ""));
                    } else {
                        //审核节点或者并行网关-汇聚到并行网关-分支
                        //判断上一个节点是否是会签
                        if (WorkflowStepDto.TYPE_COUNTERSIGN.equals(workflowStepDtos.get(y - 1).getType())) {
                            process.addFlowElement(createSequenceFlow("parallelGateway-join" + (y - 1), "parallelGateway-fork" + y, "parallelGateway-join-parallelGateway-fork-分支" + y, ""));
                        } else {
                            process.addFlowElement(createSequenceFlow("task" + (y - 1), "parallelGateway-fork" + y, "task-repulse" + y, ""));
                        }
                    }
                    //并行网关-分支和会签用户连线，会签用户和并行网关-汇聚连线
                    List<WorkflowStepStaffDto> userList = step.getWorkflowStepStaffs();
                    for (int u = 0; u < userList.size(); u++) {
                        process.addFlowElement(createSequenceFlow("parallelGateway-fork" + y, "userTask" + y + u, "parallelGateway-fork-userTask" + y + u, ""));
                        process.addFlowElement(createSequenceFlow("userTask" + y + u, "parallelGateway-join" + y, "userTask-parallelGateway-join", ""));
                        if (u == (userList.size() - 1)) {
                            process.addFlowElement(createSequenceFlow("parallelGateway-join" + y, "repulse" + y, "parallelGateway-join-repulse", "${flag=='false'}"));
                            process.addFlowElement(createSequenceFlow("repulse" + y, "task" + getNormal(workflowStepDtos, y), "repulse-task" + y, ""));
                        }
                    }
                    //最后一个节点  并行网关-汇聚到结束节点
                    if (y == (workflowStepDtos.size() - 1)) {
                        process.addFlowElement(createSequenceFlow("repulse" + y, "endEvent", "parallelGateway-join-endEvent", "${flag=='true'}"));
                    }
                } else {
                    //普通流转
                    //第一个节点
                    if (y == 0) {
                        //开始节点和审核节点1
                        process.addFlowElement(createSequenceFlow("startEvent", "task" + y, "startEvent-task" + y, ""));
                    } else {
                        //判断上一个节点是否会签
                        if (WorkflowStepDto.TYPE_COUNTERSIGN.equals(workflowStepDtos.get(y - 1).getType())) {
                            //会签
                            //并行网关-汇聚到审核节点
                            process.addFlowElement(createSequenceFlow("parallelGateway-join" + (y - 1), "task" + y, "parallelGateway-join-task" + y, ""));
                        } else {
                            //普通
                            process.addFlowElement(createSequenceFlow("task" + (y - 1), "task" + y, "task" + (y - 1) + "task" + y, "${flag=='true'}"));
                        }
                    }
                    //是否最后一个节点
                    if (y == (workflowStepDtos.size() - 1)) {
                        //审核节点到结束节点
                        process.addFlowElement(createSequenceFlow("repulse" + y, "endEvent", "task" + y + "endEvent", "${flag=='true'}"));
                        process.addFlowElement(createSequenceFlow("task" + y, "repulse" + y, "task-repulse" + y, ""));
                    } else {
                        //审核节点到回退节点
                        process.addFlowElement(createSequenceFlow("task" + y, "repulse" + y, "task-repulse" + y, "${flag=='false'}"));
                    }
                    process.addFlowElement(createSequenceFlow("repulse" + y, "task" + y, "repulse-task" + y, ""));
                }
            }

            // 2. 生成的图形信息
            new BpmnAutoLayout(model).execute();

            // 3. 部署流程
            Deployment deployment = processEngine.getRepositoryService().createDeployment()
                    .addBpmnModel(process.getId() + ".bpmn", model).name(process.getId() + "_deployment").deploy();
            workflowDto.setProcessDefinitionKey(deployment.getId());
            //        // 4. 启动一个流程实例
//        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey(process.getId());
//
//        // 5. 获取流程任务
//        List<Task> tasks = processEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).list();
//        try{
//            // 6. 将流程图保存到本地文件
//            InputStream processDiagram = processEngine.getRepositoryService().getProcessDiagram(processInstance.getProcessDefinitionId());
//            FileUtils.copyInputStreamToFile(processDiagram, new File("/deployments/"+process.getId()+".png"));
//
//            // 7. 保存BPMN.xml到本地文件
//            InputStream processBpmn = processEngine.getRepositoryService().getResourceAsStream(deployment.getId(), process.getId()+".bpmn");
//            FileUtils.copyInputStreamToFile(processBpmn,new File("/deployments/"+process.getId()+".bpmn"));
//        }catch (Exception e){
//            e.printStackTrace();
//        }
        } catch (Exception e) {
            logger.error("部署工作流失败", e);
        }

        logger.debug("工作流部署完成");
        return workflowDto;
    }

    private int getNormal(List<WorkflowStepDto> workflowStepDtos, int y) {
        for (int stepIndex = y; stepIndex > 0; stepIndex--) {
            if (WorkflowStepDto.TYPE_NORMAL.equals(workflowStepDtos.get(stepIndex).getType())) {
                return stepIndex;
            }
        }

        return 0;
    }


    //任务节点-组
    protected UserTask createGroupTask(String id, String name, String candidateGroup) {
        List<String> candidateGroups = new ArrayList<String>();
        candidateGroups.add(candidateGroup);
        UserTask userTask = new UserTask();
        userTask.setName(name);
        userTask.setId(id);
        userTask.setCandidateGroups(candidateGroups);
        return userTask;
    }

    //任务节点-用户
    protected UserTask createUserTask(String id, String name, String userPkno) {
        List<String> candidateUsers = new ArrayList<String>();
        candidateUsers.add(userPkno);
        UserTask userTask = new UserTask();
        userTask.setName(name);
        userTask.setId(id);
        userTask.setAssignee(userPkno);
        userTask.setCandidateUsers(candidateUsers);
        return userTask;
    }

    //任务节点-锁定者
    protected UserTask createAssigneeTask(String id, String name, String assignee) {
        UserTask userTask = new UserTask();
        userTask.setName(name);
        userTask.setId(id);
        userTask.setAssignee(assignee);
        return userTask;
    }

    /*连线*/
    protected SequenceFlow createSequenceFlow(String from, String to, String name, String conditionExpression) {
        SequenceFlow flow = new SequenceFlow();
        flow.setSourceRef(from);
        flow.setTargetRef(to);
        flow.setName(name);
        if (!StringUtil.isEmpty(conditionExpression)) {
            flow.setConditionExpression(conditionExpression);
        }
        return flow;
    }

    //排他网关
    protected ExclusiveGateway createExclusiveGateway(String id, String name) {
        ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
        exclusiveGateway.setId(id);
        exclusiveGateway.setName(name);
        return exclusiveGateway;
    }

    //并行网关
    protected ParallelGateway createParallelGateway(String id, String name) {
        ParallelGateway gateway = new ParallelGateway();
        gateway.setId(id);
        gateway.setName(name);
        return gateway;
    }

    //开始节点
    protected StartEvent createStartEvent() {
        StartEvent startEvent = new StartEvent();
        startEvent.setId("startEvent");
        return startEvent;
    }

    /*结束节点*/
    protected EndEvent createEndEvent() {
        EndEvent endEvent = new EndEvent();
        endEvent.setId("endEvent");
        return endEvent;
    }

    @Override
    public int queryWorkflowsCount(@RequestBody WorkflowDto workflowDto) {
        return workflowServiceDaoImpl.queryWorkflowsCount(BeanConvertUtil.beanCovertMap(workflowDto));
    }

    public IWorkflowServiceDao getWorkflowServiceDaoImpl() {
        return workflowServiceDaoImpl;
    }

    public void setWorkflowServiceDaoImpl(IWorkflowServiceDao workflowServiceDaoImpl) {
        this.workflowServiceDaoImpl = workflowServiceDaoImpl;
    }

    public IUserInnerServiceSMO getUserInnerServiceSMOImpl() {
        return userInnerServiceSMOImpl;
    }

    public void setUserInnerServiceSMOImpl(IUserInnerServiceSMO userInnerServiceSMOImpl) {
        this.userInnerServiceSMOImpl = userInnerServiceSMOImpl;
    }
}
