package in.hertz.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hertz.sel.util.UserContext;

import in.hertz.dao.DiscomRepository;
import in.hertz.dao.DraftRepository;
import in.hertz.dao.EnergySourceRepository;
import in.hertz.dao.ExBusCapacityRepository;
import in.hertz.dao.GenerationRepository;
import in.hertz.dao.GenerationUnitTypeRepository;
import in.hertz.dao.OAConsumerRepository;
import in.hertz.dao.PSSRepository;
import in.hertz.dao.QCARepository;
import in.hertz.dao.QcaPssRepository;
import in.hertz.dao.RemarksRepository;
import in.hertz.dao.TypeOfUtilitiesRepository;
import in.hertz.dao.UtilitiesTraderGencoRepository;
import in.hertz.dao.WorkflowRepository;
import in.hertz.samast.domain.ApprovalScreensRequestBO;
import in.hertz.samast.domain.BankDetailsDTO;
import in.hertz.samast.domain.BankGuaranteeDetailsDTO;
import in.hertz.samast.domain.BusinessAddressDTO;
import in.hertz.samast.domain.ConnectivityDetailsDTO;
import in.hertz.samast.domain.ContactPersonDTO;
import in.hertz.samast.domain.DeRegisterDTO;
import in.hertz.samast.domain.DraftDTO;
import in.hertz.samast.domain.EntityRegistrationDTO;
import in.hertz.samast.domain.GeneratorDetailsDTO;
import in.hertz.samast.domain.GeneratorDetailsDTOType;
import in.hertz.samast.domain.GeneratorEntityScadaApprovalDTO;
import in.hertz.samast.domain.GeneratorUnitDetailsDTO;
import in.hertz.samast.domain.LicenseDetailsDTO;
import in.hertz.samast.domain.MeteringDetailsDTO;
import in.hertz.samast.domain.OaPpaDetailsDTO;
import in.hertz.samast.domain.PSSDetailsDTO;
import in.hertz.samast.domain.QCADashboardResponseDTO;
import in.hertz.samast.domain.QCADashboardSubResponseDTO;
import in.hertz.samast.domain.QcaPssDraftDTO;
import in.hertz.samast.domain.REGeneratorDetailsDTO;
import in.hertz.samast.domain.REGeneratorSubDetailsDTO;
import in.hertz.samast.domain.RegistrationDetailsRequestDTO;
import in.hertz.samast.domain.RegistrationFeeDetailsDTO;
import in.hertz.samast.domain.RemarkDTO;
import in.hertz.samast.domain.ReportDashboardResponseDTO;
import in.hertz.samast.domain.ReportDashboardSubResponseDTO;
import in.hertz.samast.domain.SchedulingOnOffDTO;
import in.hertz.samast.domain.WorkflowDTO;
import in.hertz.samast.domain.exception.BusinessException;
import in.hertz.samast.entity.Discom;
import in.hertz.samast.entity.Draft;
import in.hertz.samast.entity.EnergySource;
import in.hertz.samast.entity.EntRegStatus;
import in.hertz.samast.entity.ExBusCapacity;
import in.hertz.samast.entity.Generation;
import in.hertz.samast.entity.GenerationUnitType;
import in.hertz.samast.entity.OAConsumer;
import in.hertz.samast.entity.PSS;
import in.hertz.samast.entity.QCA;
import in.hertz.samast.entity.QcaPss;
import in.hertz.samast.entity.Remark;
import in.hertz.samast.entity.TypeOfUtilities;
import in.hertz.samast.entity.UtilitiesTraderGenco;
import in.hertz.samast.entity.Workflow;
import in.hertz.samast.util.EPMDateUtils;
import in.hertz.samast.util.EntityRegistrationStatus;
import in.hertz.samast.util.EntityRegistrationType;
import in.hertz.samast.util.FunctionalityArea;
import in.hertz.samast.util.UtilityTypeUtil;

@Service
@Transactional
public class EntityRegistrationService {

	@Autowired
	private PSSRepository pssRepository;

	@Autowired
	private QCARepository qcaRepository;

	@Autowired
	private QcaPssRepository qcaPssRepository;

	@Autowired
	private DiscomRepository discomRepository;

	@Autowired
	private RemarksRepository remarksRepository;

	@Autowired
	private WorkflowRepository workflowRepository;

	@Autowired
	private OAConsumerRepository oaConsumerRepository;

	@Autowired
	private EnergySourceRepository energySourceRepository;

	@Autowired
	private ExBusCapacityRepository exBusCapacityRepository;

	@Autowired
	private TypeOfUtilitiesRepository typeOfUtilitiesRepository;

	@Autowired
	private DraftRepository<EntityRegistrationDTO> draftRepository;

	@Autowired
	private GenerationUnitTypeRepository generationUnitTypeRepository;

	@Autowired
	private AppNoAckNoRegNoCounterService appNoAckNoRegNoCounterService;

	@Autowired
	private UtilitiesTraderGencoRepository utilitiesTraderGencoRepository;

	@Autowired
	private GenerationRepository<GeneratorDetailsDTOType> generationRepository;

	@Autowired
	private EmailConfigurationService emailConfigurationService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	ObjectMapper objectMapper = new ObjectMapper();

	private static final Logger LOGGER = LogManager.getLogger(EntityRegistrationService.class);

	SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");

	SimpleDateFormat dateWithTime = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

	private HttpPost postMethod;

	private HttpClient httpClient = new DefaultHttpClient();

	@Value("${error.msg.unapprove}")
	private String unapproveErrMsg;

	@Value("${sel.org.uid}")
	private String selOrgUid;

	@Value("${sel.cookie}")
	private String selCookie;

	@Value("${sel.add.user.api.url}")
	private String selAddUserApiURL;

	@Value("${status.data.entity.de.reg}")
	private String entDeRegMsg;

	@Value("${error.msg.qca.two.yrs.de.reg}")
	private String qcaDeRegTwoYrsErrMsg;

	@Value("${error.msg.qca.pss.de.link}")
	private String qcaPssDeLinkErrMsg;

	@Value("${status.entity.de.reg.request}")
	private String entDeRegRequest;

	@Value("${error.msg.entity.name.duplicate}")
	private String entNameErrMsg;

	@Value("${error.msg.entity.name.email.duplicate}")
	private String entNamePrimaryEmailErrMsg;

	public Draft<EntityRegistrationDTO> saveDraftForEntityRegistration(DraftDTO<EntityRegistrationDTO> draftDTO)
			throws ParseException, JsonProcessingException, BusinessException {
		Draft<EntityRegistrationDTO> draft = new Draft<>();
		Map<String, WorkflowDTO> workflowMap = new LinkedHashMap<>();
		Integer draftId = draftDTO.getDraftId();
		EntityRegistrationDTO entityRegistrationDTO = draftDTO.getJsonDTO();
		String entityName = entityRegistrationDTO.getEntityName();
		String primaryEmail = entityRegistrationDTO.getBusinessAddress().getEmail();
		if (draftId == null) {
			List<Draft<EntityRegistrationDTO>> duplicateEntNameAndEmailList = draftRepository
					.findDraftByEntityNameAndPrimaryEmail(entityName, primaryEmail);
			if (CollectionUtils.isEmpty(duplicateEntNameAndEmailList)) {
				draft.setStatus(EntityRegistrationStatus.DRAFT);
				draft.setFunctionalityArea(FunctionalityArea.ENT_REG);

				List<Workflow> findSectionByFuncArea = workflowRepository
						.findSectionByFuncArea(FunctionalityArea.ENT_REG);
				findSectionByFuncArea.forEach(bean -> {
					String sectionId = bean.getSectionId();
					WorkflowDTO workflowDTO = new WorkflowDTO();
					workflowDTO.setStatus(EntityRegistrationStatus.UN_APP);
					workflowMap.put(sectionId, workflowDTO);
				});
				draft.setWorkflow(workflowMap);

				if (entityRegistrationDTO != null) {
					parseDatesForEntityRegDTO(entityRegistrationDTO);
					String regType = "APP_NO";
					String appNo = appNoAckNoRegNoCounterService
							.generateAppNoAndRegNo(entityRegistrationDTO.getEntityRegType().toUpperCase(), regType);
					entityRegistrationDTO.setAppNo(appNo);
					draft.setData(entityRegistrationDTO);
				}
				draftRepository.createUserData(draft);
				Draft<EntityRegistrationDTO> savedDraft = draftRepository.save(draft);

				String bodyTextForSaveDraft = emailConfigurationService.setBodyTextForSaveDraft(savedDraft);
				String subjectText = emailConfigurationService.setSubjectText(savedDraft);
				emailConfigurationService.sendEmail(bodyTextForSaveDraft, subjectText, primaryEmail);
				return savedDraft;
			} else {
				throw new BusinessException(entNamePrimaryEmailErrMsg);
			}
		} else {
			List<Draft<EntityRegistrationDTO>> duplicateEntNameAndEmailAndDraftIdList = draftRepository
					.findDraftByEntityNameAndPrimaryEmailAndDraftId(entityName, primaryEmail, draftId);
			if (CollectionUtils.isEmpty(duplicateEntNameAndEmailAndDraftIdList)) {
				Draft<EntityRegistrationDTO> findDraftById = draftRepository.findDraftById(draftId);
				if (findDraftById != null) {
					if (entityRegistrationDTO != null) {
						parseDatesForEntityRegDTO(entityRegistrationDTO);
						findDraftById.setData(entityRegistrationDTO);
					}
					draftRepository.updateUserData(findDraftById);
					draftRepository.save(findDraftById);
				}
				return findDraftById;
			} else {
				throw new BusinessException(entNamePrimaryEmailErrMsg);
			}
		}
	}

	public Draft<EntityRegistrationDTO> submitEntityRegistrationDraft(DraftDTO<EntityRegistrationDTO> draftDTO)
			throws ParseException, BusinessException {
		Draft<EntityRegistrationDTO> draft = new Draft<>();
		Map<String, WorkflowDTO> workflowMap = new LinkedHashMap<>();
		EntityRegistrationDTO entityRegistrationDTO = draftDTO.getJsonDTO();
		String appNo = entityRegistrationDTO.getAppNo();
		String regNo = entityRegistrationDTO.getRegNo();
		String entityName = entityRegistrationDTO.getEntityName();
		String primaryEmail = entityRegistrationDTO.getBusinessAddress().getEmail();
		Boolean isChangeRequest = draftDTO.getIsChangeRequest();
		String entityRegType = entityRegistrationDTO.getEntityRegType().toUpperCase();
		try {
			if (isChangeRequest) {
				Draft<EntityRegistrationDTO> findDraftByRegNo = draftRepository.findDraftByRegNo(regNo);
				if (findDraftByRegNo != null) {
					if (findDraftByRegNo.getStatus().equalsIgnoreCase(EntityRegistrationStatus.REV)) {
						throw new BusinessException(unapproveErrMsg);
					} else if (findDraftByRegNo.getStatus().equalsIgnoreCase(EntityRegistrationStatus.REG)) {
						Integer draftId = findDraftByRegNo.getUID();
						List<Draft<EntityRegistrationDTO>> duplicateEntityNameList = draftRepository
								.findDraftByEntityName(entityName, draftId);
						if (CollectionUtils.isEmpty(duplicateEntityNameList)) {
							findDraftByRegNo.setStatus(EntityRegistrationStatus.REV);

							if (entityRegistrationDTO != null) {
								parseDatesForEntityRegDTO(entityRegistrationDTO);
								String ackNo = appNoAckNoRegNoCounterService.generateAckNo(appNo, entityRegType);
								entityRegistrationDTO.setAckNo(ackNo);
								findDraftByRegNo.setData(entityRegistrationDTO);
							}

							Map<String, WorkflowDTO> workflowMapFromDB = findDraftByRegNo.getWorkflow();
							List<String> sectionIdList = draftDTO.getSectionIdList();
							sectionIdList.forEach(bean -> {
								if (workflowMapFromDB.containsKey(bean)) {
									WorkflowDTO workflowDTOFromDB = workflowMapFromDB.get(bean);
									workflowDTOFromDB.setStatus(EntityRegistrationStatus.UN_APP);
									workflowDTOFromDB.setInsertTime(new Date());
									workflowMapFromDB.put(bean, workflowDTOFromDB);
								}
							});
							findDraftByRegNo.setWorkflow(workflowMapFromDB);
							draftRepository.updateUserData(findDraftByRegNo);
							draftRepository.save(findDraftByRegNo);
							return findDraftByRegNo;
						} else {
							throw new BusinessException(entNameErrMsg);
						}
					}
				}
			} else {
				if (appNo != null) {
					Draft<EntityRegistrationDTO> findDraftByAppNoAndStatus = draftRepository
							.findDraftByAppNoAndStatus(appNo, EntityRegistrationStatus.DRAFT);
					if (findDraftByAppNoAndStatus != null) {
						List<Draft<EntityRegistrationDTO>> duplicateEntNameAndEmailAndDraftIdList = draftRepository
								.findDraftByEntityNameAndPrimaryEmailAndDraftId(entityName, primaryEmail,
										findDraftByAppNoAndStatus.getUID());
						if (CollectionUtils.isEmpty(duplicateEntNameAndEmailAndDraftIdList)) {
							findDraftByAppNoAndStatus.setStatus(EntityRegistrationStatus.NEW_REG);

							if (entityRegistrationDTO != null) {
								parseDatesForEntityRegDTO(entityRegistrationDTO);
								String ackNo = appNoAckNoRegNoCounterService.generateAckNo(appNo, entityRegType);
								entityRegistrationDTO.setAckNo(ackNo);
								findDraftByAppNoAndStatus.setData(entityRegistrationDTO);
							}
							draftRepository.updateUserData(findDraftByAppNoAndStatus);
							draftRepository.save(findDraftByAppNoAndStatus);
							return findDraftByAppNoAndStatus;
						} else {
							throw new BusinessException(entNamePrimaryEmailErrMsg);
						}
					}
				} else {
					List<Draft<EntityRegistrationDTO>> duplicateEntNameAndEmailList = draftRepository
							.findDraftByEntityNameAndPrimaryEmail(entityName, primaryEmail);
					if (CollectionUtils.isEmpty(duplicateEntNameAndEmailList)) {
						draft.setStatus(EntityRegistrationStatus.NEW_REG);
						draft.setFunctionalityArea(FunctionalityArea.ENT_REG);

						List<Workflow> findSectionByFuncArea = workflowRepository
								.findSectionByFuncArea(FunctionalityArea.ENT_REG);
						findSectionByFuncArea.forEach(bean -> {
							String sectionId = bean.getSectionId();
							WorkflowDTO workflowDTO = new WorkflowDTO();
							workflowDTO.setStatus(EntityRegistrationStatus.UN_APP);
							workflowDTO.setInsertTime(new Date());
							workflowMap.put(sectionId, workflowDTO);
						});
						draft.setWorkflow(workflowMap);

						if (entityRegistrationDTO != null) {
							parseDatesForEntityRegDTO(entityRegistrationDTO);
							String regType = "APP_NO";
							appNo = appNoAckNoRegNoCounterService.generateAppNoAndRegNo(entityRegType, regType);
							entityRegistrationDTO.setAppNo(appNo);

							String ackNo = appNoAckNoRegNoCounterService.generateAckNo(appNo, entityRegType);
							entityRegistrationDTO.setAckNo(ackNo);
							draft.setData(entityRegistrationDTO);
						}
						draftRepository.createUserData(draft);
						draftRepository.save(draft);
						return draft;
					} else {
						throw new BusinessException(entNamePrimaryEmailErrMsg);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		return null;
	}

	public Draft<EntityRegistrationDTO> submitEntityRegistrationDraftForQcaPss(QcaPssDraftDTO qcaPssDraftDTO)
			throws BusinessException {
		Draft<EntityRegistrationDTO> draft = new Draft<>();
		Map<String, WorkflowDTO> workflowMap = new LinkedHashMap<>();
		EntityRegistrationDTO entityRegistrationDTO = qcaPssDraftDTO.getEntityRegistrationDTO();
		String appNoFromDB = entityRegistrationDTO.getAppNo();
		String regNo = entityRegistrationDTO.getRegNo();
		Integer qcaUtgId = qcaPssDraftDTO.getQcaUtgId();
		Boolean isChangeRequest = qcaPssDraftDTO.getIsChangeRequest();
		String entityRegType = entityRegistrationDTO.getEntityRegType().toUpperCase();
		try {
			if (isChangeRequest) {
				Draft<EntityRegistrationDTO> findDraftByRegNo = draftRepository.findDraftByRegNo(regNo);
				if (findDraftByRegNo != null) {
					if (findDraftByRegNo.getStatus().equalsIgnoreCase(EntityRegistrationStatus.REV)) {
						throw new BusinessException(unapproveErrMsg);
					} else if (findDraftByRegNo.getStatus().equalsIgnoreCase(EntityRegistrationStatus.REG)) {
						findDraftByRegNo.setStatus(EntityRegistrationStatus.REV);

						if (entityRegistrationDTO != null) {
							parseDatesForEntityRegDTO(entityRegistrationDTO);
							String ackNo = appNoAckNoRegNoCounterService.generateAckNo(appNoFromDB, entityRegType);
							entityRegistrationDTO.setAckNo(ackNo);
							findDraftByRegNo.setData(entityRegistrationDTO);
						}

						Map<String, WorkflowDTO> workflowMapFromDB = findDraftByRegNo.getWorkflow();
						List<String> sectionIdList = qcaPssDraftDTO.getSectionIdList();
						sectionIdList.forEach(bean -> {
							if (workflowMapFromDB.containsKey(bean)) {
								WorkflowDTO workflowDTOFromDB = workflowMapFromDB.get(bean);
								workflowDTOFromDB.setStatus(EntityRegistrationStatus.UN_APP);
								workflowDTOFromDB.setInsertTime(new Date());
								workflowMapFromDB.put(bean, workflowDTOFromDB);
							}
						});
						findDraftByRegNo.setWorkflow(workflowMapFromDB);
						draftRepository.updateUserData(findDraftByRegNo);
						draftRepository.save(findDraftByRegNo);
						return findDraftByRegNo;
					}
				}
			} else {
				draft.setStatus(EntityRegistrationStatus.NEW_REG);
				draft.setFunctionalityArea(FunctionalityArea.ENT_REG);

				UtilitiesTraderGenco utgDetails = utilitiesTraderGencoRepository.findByUtgId(qcaUtgId);
				QCA findByUtgId = qcaRepository.findByUtgId(utgDetails);

				List<Workflow> findSectionByFuncArea = workflowRepository
						.findSectionByFuncArea(FunctionalityArea.ENT_REG);
				findSectionByFuncArea.forEach(bean -> {
					String sectionId = bean.getSectionId();
					WorkflowDTO workflowDTO = new WorkflowDTO();
					workflowDTO.setStatus(EntityRegistrationStatus.UN_APP);
					workflowMap.put(sectionId, workflowDTO);
				});
				draft.setWorkflow(workflowMap);
				if (entityRegistrationDTO != null) {
					parseDatesForEntityRegDTO(entityRegistrationDTO);
					String regType = "APP_NO";
					String appNo = appNoAckNoRegNoCounterService.generateAppNoAndRegNo(entityRegType, regType);
					entityRegistrationDTO.setAppNo(appNo);

					String ackNo = appNoAckNoRegNoCounterService.generateAckNo(appNo, entityRegType);
					entityRegistrationDTO.setAckNo(ackNo);
					entityRegistrationDTO.setQcaId(findByUtgId.getQcaUid());
					entityRegistrationDTO.setEntityName(findByUtgId.getPlantName());
					draft.setData(entityRegistrationDTO);
				}
				draftRepository.createUserData(draft);
				draftRepository.save(draft);
				return draft;
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public List<Draft> getRegistrationDetailsForDashboard(Map<String, Object> requestBody) {
		List<String> appTypeList = (List<String>) requestBody.get("appTypeList");
		List<String> entityTypeList = (List<String>) requestBody.get("entityTypeList");
		Double fromQtm = (Double) requestBody.get("fromQtm");
		Double toQtm = (Double) requestBody.get("toQtm");
		List<Integer> voltageLevelList = (List<Integer>) requestBody.get("voltageLevelList");

		List<Draft> registrationDetails = queryForFetchingDraftDetailsForEntDashboard(appTypeList, entityTypeList,
				fromQtm, toQtm, voltageLevelList);
		for (Draft draft : registrationDetails) {
			EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(draft.getData(),
					EntityRegistrationDTO.class);
			String ackNo = entityRegistrationDTO.getAckNo();
			List<Remark> findRemarkByAckNo = remarksRepository.findRemarkByAckNo(ackNo);
			List<RemarkDTO> remarkDTOList = new ArrayList<>();
			for (Remark remarkObj : findRemarkByAckNo) {
				RemarkDTO remarkDTO = new RemarkDTO();
				remarkDTO.setAckNo(remarkObj.getAckNo());
				remarkDTO.setCreatedDate(remarkObj.getInsertTime());
				remarkDTO.setDepartment(remarkObj.getDepartment());
				remarkDTO.setRemarks(remarkObj.getRemarks());
				remarkDTO.setUserName(remarkObj.getCreatedBy());
				remarkDTOList.add(remarkDTO);
			}
			entityRegistrationDTO.setRemarkDTOList(remarkDTOList);
			draft.setData(entityRegistrationDTO);
		}
		return registrationDetails;
	}

	public List<Draft> queryForFetchingDraftDetailsForEntDashboard(List<String> appTypeList,
			List<String> entityTypeList, Double fromQtm, Double toQtm, List<Integer> voltageLevelList) {
		List<Predicate> predicates = new ArrayList<>();
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Draft> criteriaQuery = criteriaBuilder.createQuery(Draft.class);
		Root<Draft> root = criteriaQuery.from(Draft.class);
		criteriaQuery.select(root);
		Expression<String> parentExpression = root.get("status");
		if (CollectionUtils.isNotEmpty(entityTypeList)) {
			Predicate entityTypePredicate = criteriaBuilder
					.function("JSON_EXTRACT", String.class, root.get("data"), criteriaBuilder.literal("$.entityType"))
					.in(entityTypeList);
			predicates.add(entityTypePredicate);
		}
		if (CollectionUtils.isNotEmpty(appTypeList)) {
			Predicate appTypePredicate = parentExpression.in(appTypeList);
			predicates.add(appTypePredicate);
		} else {
			Predicate statusPredicateDraft = criteriaBuilder.notEqual(root.get("status"),
					EntityRegistrationStatus.DRAFT);
			Predicate statusPredicateCancel = criteriaBuilder.notEqual(root.get("status"),
					EntityRegistrationStatus.CANCELLED);
			Predicate statusPredicateDelete = criteriaBuilder.notEqual(root.get("status"),
					EntityRegistrationStatus.DELETE);
			Predicate draftAndCancelStatusPredicate = criteriaBuilder.and(statusPredicateDraft, statusPredicateCancel,
					statusPredicateDelete);
			predicates.add(draftAndCancelStatusPredicate);
		}
//		if (fromQtm != null) {
//			
//			Predicate totalICPredicateForREG = criteriaBuilder
//					.lessThanOrEqualTo(criteriaBuilder.function("JSON_EXTRACT", Double.class, root.get("data"),
//							criteriaBuilder.literal("$.reGeneratorDetails.totalInstalledCapacity")), toQtm);
//			Predicate ncpdQuantumPredicateForSEN = criteriaBuilder.lessThanOrEqualTo(criteriaBuilder.function(
//					"JSON_EXTRACT", Double.class, root.get("data"), criteriaBuilder.literal("$.ncpdQuantum")), toQtm);
//			Predicate totalICPredicateForSEN = criteriaBuilder.lessThanOrEqualTo(criteriaBuilder.function(
//					"JSON_EXTRACT", Double.class, root.get("data"), criteriaBuilder.literal("$.ncpdQuantum")), toQtm);
//
//		}
		if (CollectionUtils.isNotEmpty(voltageLevelList)) {
			Predicate voltageLevelPredicateForSEN = criteriaBuilder.function("JSON_EXTRACT", Integer.class,
					root.get("data"), criteriaBuilder.literal("$.connectivityDetails.voltageLevel"))
					.in(voltageLevelList);
			Predicate voltageLevelPredicateForREGandQCA = criteriaBuilder.function("JSON_EXTRACT", Integer.class,
					root.get("data"), criteriaBuilder.literal("$.pssDetails.voltageLevel")).in(voltageLevelList);

			Predicate finalPredicateForVoltageLevel = criteriaBuilder.or(voltageLevelPredicateForSEN,
					voltageLevelPredicateForREGandQCA);
			predicates.add(finalPredicateForVoltageLevel);
		}
		Predicate functionalityAreaPredicate = criteriaBuilder.equal(root.get("functionalityArea"),
				FunctionalityArea.ENT_REG);
		predicates.add(functionalityAreaPredicate);

		Predicate finalPredicate = criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
		criteriaQuery.where(finalPredicate);
		List<Draft> registrationDetails = entityManager.createQuery(criteriaQuery).getResultList();
		return registrationDetails;
	}

	private void parseDatesForEntityRegDTO(EntityRegistrationDTO entityRegistrationDTO) throws ParseException {
		if (entityRegistrationDTO != null) {
			LicenseDetailsDTO licenseDetails = entityRegistrationDTO.getLicenseDetails();
			if (licenseDetails != null) {
				String licenseValidityStr = licenseDetails.getLicenseValidityStr();
				if (StringUtils.isNotBlank(licenseValidityStr)) {
					licenseDetails.setLicenseValidity(sdf.parse(licenseValidityStr));
				}

				String licenseDateStr = licenseDetails.getLicenseDateStr();
				if (StringUtils.isNotBlank(licenseDateStr)) {
					licenseDetails.setLicenseDate(sdf.parse(licenseDateStr));
				}
			}

			Double totalExBusCapacity = 0.0;
			GeneratorDetailsDTO generatorDetails = entityRegistrationDTO.getGeneratorDetails();
			if (generatorDetails != null) {
				List<GeneratorUnitDetailsDTO> generatorUnitDetails = generatorDetails.getGeneratorUnitDetails();
				if (CollectionUtils.isNotEmpty(generatorUnitDetails)) {
					for (GeneratorUnitDetailsDTO generatorUnitDetailsDTO : generatorUnitDetails) {
						String codStr = generatorUnitDetailsDTO.getCodStr();
						if (StringUtils.isNotBlank(codStr)) {
							generatorUnitDetailsDTO.setCod(sdf.parse(codStr));
						}
						Double auxiliaryConsumption = generatorUnitDetailsDTO.getAuxiliaryConsumption();
						Double installedCapacity = generatorUnitDetailsDTO.getInstalledCapacity();
						Double exBusCapacity = installedCapacity - ((auxiliaryConsumption * installedCapacity) / 100);
						totalExBusCapacity = totalExBusCapacity + exBusCapacity;
					}
					entityRegistrationDTO.setTotalExBusCapacity(totalExBusCapacity);
				}
			}

			REGeneratorDetailsDTO reGeneratorDetails = entityRegistrationDTO.getReGeneratorDetails();
			if (reGeneratorDetails != null) {
				String codStr = reGeneratorDetails.getCodStr();
				if (StringUtils.isNotBlank(codStr)) {
					reGeneratorDetails.setCod(dateWithTime.parse(codStr));
				}
				List<REGeneratorSubDetailsDTO> reGeneratorSubDetails = reGeneratorDetails.getReGeneratorSubDetails();
				if (CollectionUtils.isNotEmpty(reGeneratorSubDetails)) {
					for (REGeneratorSubDetailsDTO reGeneratorSubDetailsDTO : reGeneratorSubDetails) {
						String ppaValidityStr = reGeneratorSubDetailsDTO.getPpaValidityStr();
						if (StringUtils.isNotBlank(ppaValidityStr)) {
							reGeneratorSubDetailsDTO.setPpaValidity(sdf.parse(ppaValidityStr));
						}
					}
				}
			}

			List<OaPpaDetailsDTO> oaDetails = entityRegistrationDTO.getOaDetails();
			if (CollectionUtils.isNotEmpty(oaDetails)) {
				for (OaPpaDetailsDTO oaDetailsDTO : oaDetails) {
					String validityStr = oaDetailsDTO.getValidityStr();
					if (StringUtils.isNotBlank(validityStr)) {
						oaDetailsDTO.setValidity(sdf.parse(validityStr));
					}
				}
			}

			List<OaPpaDetailsDTO> ppaDetails = entityRegistrationDTO.getPpaDetails();
			if (CollectionUtils.isNotEmpty(ppaDetails)) {
				for (OaPpaDetailsDTO ppaDetailsDTO : ppaDetails) {
					String validityStr = ppaDetailsDTO.getValidityStr();
					if (StringUtils.isNotBlank(validityStr)) {
						ppaDetailsDTO.setValidity(sdf.parse(validityStr));
					}
				}
			}

			BankGuaranteeDetailsDTO bankGuaranteeDetails = entityRegistrationDTO.getBankGuaranteeDetails();
			if (bankGuaranteeDetails != null) {
				String bgDateStr = bankGuaranteeDetails.getBgDateStr();
				if (StringUtils.isNotBlank(bgDateStr)) {
					bankGuaranteeDetails.setBgDate(sdf.parse(bgDateStr));
				}
			}

			String lcLmDateStr = entityRegistrationDTO.getLcLmDateStr();
			if (StringUtils.isNotBlank(lcLmDateStr)) {
				entityRegistrationDTO.setLcLmDate(sdf.parse(lcLmDateStr));
			}
		}
	}

	public Draft<EntityRegistrationDTO> getRegistrationDetailsByDraftId(Integer draftId) {
		Draft<EntityRegistrationDTO> findDraftById = draftRepository.findDraftById(draftId);
		EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(findDraftById.getData(),
				EntityRegistrationDTO.class);
		String entityType = entityRegistrationDTO.getEntityType();
		if (entityType.equals(EntityRegistrationType.QCA_PSS)) {
			Integer pssId = entityRegistrationDTO.getPssDetails().getPssId();
			List<Generation<GeneratorDetailsDTOType>> findByPssId = generationRepository.findByPssId(pssId);
			if (CollectionUtils.isNotEmpty(findByPssId)) {
				List<REGeneratorDetailsDTO> reGeneratorDetailsDTOList = new ArrayList<>();
				findByPssId.forEach(bean -> {
					REGeneratorDetailsDTO reGeneratorDetailsDTO = (REGeneratorDetailsDTO) objectMapper
							.convertValue(bean.getGeneratorDetails(), REGeneratorDetailsDTO.class);
					reGeneratorDetailsDTOList.add(reGeneratorDetailsDTO);
				});
				entityRegistrationDTO.setReGeneratorDetailsDTOList(reGeneratorDetailsDTOList);
				findDraftById.setData(entityRegistrationDTO);
			}
		}
		return findDraftById;
	}

	public Draft<EntityRegistrationDTO> findDraftByAcknowledgementNo(String ackNo) {
		return draftRepository.findDraftByAckNoStatus(ackNo);
	}

	public Draft<EntityRegistrationDTO> getRegistrationCancel(String ackNo) {
		return draftRepository.findDraftByAckNoStatusCancel(ackNo);

	}

	public Draft<EntityRegistrationDTO> cancelRegistrationByAckNo(String ackNo) {
		Draft<EntityRegistrationDTO> findDraftByAcknowledgementNo = getRegistrationCancel(ackNo);
		if (Objects.nonNull(findDraftByAcknowledgementNo)) {
			findDraftByAcknowledgementNo.setStatus(EntityRegistrationStatus.CANCELLED);
			return draftRepository.save(findDraftByAcknowledgementNo);
		}
		return findDraftByAcknowledgementNo;
	}

	public Remark departmentApprovals(ApprovalScreensRequestBO approvalScreensRequestBO)
			throws JsonProcessingException, ClientProtocolException, IOException, ParseException {
		Integer draftId = approvalScreensRequestBO.getDraftId();
		String sectionId = approvalScreensRequestBO.getSectionId();
		String remarks = approvalScreensRequestBO.getRemarks();
		String status = approvalScreensRequestBO.getStatus();
		String entityRegType = approvalScreensRequestBO.getEntityRegType().toUpperCase();
		Double lcLmAmount = approvalScreensRequestBO.getLcLmAmount();
		String lcLmValidUptoDateStr = approvalScreensRequestBO.getLcLmValidUptoDateStr();
		BankGuaranteeDetailsDTO bankGuaranteeDetails = approvalScreensRequestBO.getBankGuaranteeDetails();
		GeneratorEntityScadaApprovalDTO scadaData = approvalScreensRequestBO.getScadaData();
		Workflow workflowObj = workflowRepository.findDepartmentBySectionId(sectionId);
		String department = workflowObj.getDepartment();

		Draft<EntityRegistrationDTO> draftDetails = draftRepository.findDraftById(draftId);
		if (draftDetails != null) {
			Map<String, WorkflowDTO> workflowMap = draftDetails.getWorkflow();
			EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(draftDetails.getData(),
					EntityRegistrationDTO.class);
			String ackNo = entityRegistrationDTO.getAckNo();
			String draftStatus = draftDetails.getStatus();
			if (workflowMap.containsKey(sectionId)) {
				if (draftStatus.equals(EntityRegistrationStatus.REV)) {
					String draftRegNo = entityRegistrationDTO.getRegNo();
					switch (status) {
					case EntityRegistrationStatus.REJECT:
					case EntityRegistrationStatus.HOLD:
						setWorkflowDTO(workflowMap, sectionId, status);
						draftDetails.setStatus(status);
						if (sectionId.equals("002")) {
							entityRegistrationDTO.setScadaData(scadaData);
						}
						if (sectionId.equals("004")) {
							if (lcLmAmount != null) {
								entityRegistrationDTO.setLcLmAmount(lcLmAmount);
							}
							if (StringUtils.isNotBlank(lcLmValidUptoDateStr)) {
								entityRegistrationDTO.setLcLmValidUptoDateStr(lcLmValidUptoDateStr);
								entityRegistrationDTO.setLcLmValidUptoDate(sdf.parse(lcLmValidUptoDateStr));
							}
							if (bankGuaranteeDetails != null) {
								String bgValidUptoDateStr = bankGuaranteeDetails.getBgValidUptoDateStr();
								if (StringUtils.isNotBlank(bgValidUptoDateStr)) {
									entityRegistrationDTO.getBankGuaranteeDetails()
											.setBgValidUptoDateStr(bgValidUptoDateStr);
									entityRegistrationDTO.getBankGuaranteeDetails()
											.setBgValidUptoDate(sdf.parse(bgValidUptoDateStr));
								}
							}
						}
						break;
					case EntityRegistrationStatus.APP:
						setWorkflowDTO(workflowMap, sectionId, status);
						if (sectionId.equals("002")) {
							entityRegistrationDTO.setScadaData(scadaData);
						}
						if (sectionId.equals("003")) {
							switch (entityRegType) {
							case EntityRegistrationType.SEN:
							case EntityRegistrationType.REG:
							case EntityRegistrationType.QCA:
							case EntityRegistrationType.QCA_PSS:
								draftDetails.setStatus(EntityRegistrationStatus.REG);
								checkEntityRegTypeForSendingRevisedDataIntoMasterTables(entityRegistrationDTO,
										draftRegNo, entityRegType);
								break;
							default:
								break;
							}
						}
						if (sectionId.equals("004")) {
							switch (entityRegType) {
							case EntityRegistrationType.SEN:
								if (lcLmAmount != null) {
									entityRegistrationDTO.setLcLmAmount(lcLmAmount);
								}
								if (StringUtils.isNotBlank(lcLmValidUptoDateStr)) {
									entityRegistrationDTO.setLcLmValidUptoDateStr(lcLmValidUptoDateStr);
									entityRegistrationDTO.setLcLmValidUptoDate(sdf.parse(lcLmValidUptoDateStr));
								}
								Generation<GeneratorDetailsDTOType> genRevDetails = generationRepository
										.findByRegNo(draftRegNo);
								genRevDetails.setLcLmAmount(entityRegistrationDTO.getLcLmAmount());
								genRevDetails.setLcLmValidUptoDate(entityRegistrationDTO.getLcLmValidUptoDate());
								generationRepository.save(genRevDetails);
								break;
							case EntityRegistrationType.REG:
							case EntityRegistrationType.QCA:
								break;
							case EntityRegistrationType.QCA_PSS:
								if (bankGuaranteeDetails != null) {
									String bgValidUptoDateStr = bankGuaranteeDetails.getBgValidUptoDateStr();
									if (StringUtils.isNotBlank(bgValidUptoDateStr)) {
										entityRegistrationDTO.getBankGuaranteeDetails()
												.setBgValidUptoDateStr(bgValidUptoDateStr);
										entityRegistrationDTO.getBankGuaranteeDetails()
												.setBgValidUptoDate(sdf.parse(bgValidUptoDateStr));
									}
								}
								QcaPss qcaPssRevDetails = qcaPssRepository.findByRegNo(draftRegNo);
								qcaPssRevDetails
										.setBankGuaranteeDetails(entityRegistrationDTO.getBankGuaranteeDetails());
								break;
							default:
								break;
							}
						}
						break;
					default:
						break;
					}
				} else {
					switch (status) {
					case EntityRegistrationStatus.REJECT:
					case EntityRegistrationStatus.HOLD:
						setWorkflowDTO(workflowMap, sectionId, status);
						draftDetails.setStatus(status);
						if (sectionId.equals("002")) {
							entityRegistrationDTO.setScadaData(scadaData);
						}
						if (sectionId.equals("004")) {
							if (lcLmAmount != null) {
								entityRegistrationDTO.setLcLmAmount(lcLmAmount);
							}
							if (StringUtils.isNotBlank(lcLmValidUptoDateStr)) {
								entityRegistrationDTO.setLcLmValidUptoDateStr(lcLmValidUptoDateStr);
								entityRegistrationDTO.setLcLmValidUptoDate(sdf.parse(lcLmValidUptoDateStr));
							}
							if (bankGuaranteeDetails != null) {
								String bgValidUptoDateStr = bankGuaranteeDetails.getBgValidUptoDateStr();
								if (StringUtils.isNotBlank(bgValidUptoDateStr)) {
									entityRegistrationDTO.getBankGuaranteeDetails()
											.setBgValidUptoDateStr(bgValidUptoDateStr);
									entityRegistrationDTO.getBankGuaranteeDetails()
											.setBgValidUptoDate(sdf.parse(bgValidUptoDateStr));
								}
							}
						}
						break;
					case EntityRegistrationStatus.APP:
						setWorkflowDTO(workflowMap, sectionId, status);
						if (sectionId.equals("002")) {
							entityRegistrationDTO.setScadaData(scadaData);
						}
						if (sectionId.equals("003")) {
							switch (entityRegType) {
							case EntityRegistrationType.SEN:
							case EntityRegistrationType.REG:
							case EntityRegistrationType.QCA:
							case EntityRegistrationType.QCA_PSS:
								String regType = "REG_NO";
								String regNo = appNoAckNoRegNoCounterService.generateAppNoAndRegNo(entityRegType,
										regType);
								entityRegistrationDTO.setRegNo(regNo);
								draftDetails.setStatus(EntityRegistrationStatus.REG);
								checkEntityRegTypeForSendingMasterData(entityRegistrationDTO, regNo, entityRegType);

								JsonObject jsonObject = new JsonObject();
								jsonObject.addProperty("prefix", "");
								jsonObject.addProperty("firstName", entityRegistrationDTO.getEntityName());
								jsonObject.addProperty("middleName", "");
								jsonObject.addProperty("lastName", entityRegistrationDTO.getEntityName());
								jsonObject.addProperty("gender", "Male");
								jsonObject.addProperty("employeeID", "");
								jsonObject.addProperty("designation", "");
								jsonObject.addProperty("department", "");
								jsonObject.addProperty("emailID",
										entityRegistrationDTO.getBusinessAddress().getEmail());
								jsonObject.addProperty("contactNo",
										entityRegistrationDTO.getBusinessAddress().getMobileNo());
								jsonObject.addProperty("profilePic", "");
								JsonArray jsonArray = new JsonArray();
								jsonObject.add("roles", jsonArray);
								jsonObject.addProperty("orgUid", selOrgUid);
								jsonObject.addProperty("timeZone", 1);
								// String userObjJson = objectMapper.writeValueAsString(jsonObject);
								String newUserAddedInAuth = sendDataWithHeader(selAddUserApiURL, jsonObject.toString());
								break;
							default:
								break;
							}
						}
						if (sectionId.equals("004")) {
							switch (entityRegType) {
							case EntityRegistrationType.SEN:
								if (lcLmAmount != null) {
									entityRegistrationDTO.setLcLmAmount(lcLmAmount);
								}
								if (StringUtils.isNotBlank(lcLmValidUptoDateStr)) {
									entityRegistrationDTO.setLcLmValidUptoDateStr(lcLmValidUptoDateStr);
									entityRegistrationDTO.setLcLmValidUptoDate(sdf.parse(lcLmValidUptoDateStr));
								}
								Generation<GeneratorDetailsDTOType> genDetails = generationRepository
										.findByRegNo(entityRegistrationDTO.getRegNo());
								genDetails.setLcLmAmount(entityRegistrationDTO.getLcLmAmount());
								genDetails.setLcLmValidUptoDate(entityRegistrationDTO.getLcLmValidUptoDate());
								generationRepository.save(genDetails);
								break;
							case EntityRegistrationType.REG:
							case EntityRegistrationType.QCA:
								break;
							case EntityRegistrationType.QCA_PSS:
								if (bankGuaranteeDetails != null) {
									String bgValidUptoDateStr = bankGuaranteeDetails.getBgValidUptoDateStr();
									if (StringUtils.isNotBlank(bgValidUptoDateStr)) {
										entityRegistrationDTO.getBankGuaranteeDetails()
												.setBgValidUptoDateStr(bgValidUptoDateStr);
										entityRegistrationDTO.getBankGuaranteeDetails()
												.setBgValidUptoDate(sdf.parse(bgValidUptoDateStr));
									}
								}
								setSchedulingForQcaPss(entityRegistrationDTO, entityRegType);
								break;
							default:
								break;
							}
						}
						break;
					default:
						break;
					}
				}
			}
			draftDetails.setWorkflow(workflowMap);
			draftDetails.setData(entityRegistrationDTO);
			draftRepository.save(draftDetails);

			Remark remarkObj = setRemarks(ackNo, department, remarks);
			return remarkObj;
		} else {
			return null;
		}
	}

	public Remark setRemarks(String ackNo, String department, String remarks) {
		Remark remarkObj = new Remark();
		remarkObj.setAckNo(ackNo);
		remarkObj.setDepartment(department);
		remarkObj.setRemarks(remarks);
		remarksRepository.createUserData(remarkObj);
		remarksRepository.save(remarkObj);
		return remarkObj;
	}

	public void setWorkflowDTO(Map<String, WorkflowDTO> workflowMap, String sectionId, String status) {
		WorkflowDTO workflowDTO = workflowMap.get(sectionId);
		workflowDTO.setInsertTime(new Date());
		workflowDTO.setStatus(status);
		workflowDTO.setUserName(UserContext.getEmailID());
		workflowMap.put(sectionId, workflowDTO);
	}

	public void checkEntityRegTypeForSendingMasterData(EntityRegistrationDTO entityRegistrationDTO, String regNo,
			String entityRegType) {
		switch (entityRegType) {
		case EntityRegistrationType.SEN:
			checkEntityTypeForSENRegTypeForSendingMasterData(entityRegistrationDTO, regNo);
			break;
		case EntityRegistrationType.REG:
			setMasterDataForREGType(entityRegistrationDTO, regNo);
			break;
		case EntityRegistrationType.QCA:
			setMasterDataForQCAType(entityRegistrationDTO, regNo);
			break;
		case EntityRegistrationType.QCA_PSS:
			setMasterDataForQcaPssType(entityRegistrationDTO, regNo);
		default:
			break;
		}
	}

	public void checkEntityTypeForSENRegTypeForSendingMasterData(EntityRegistrationDTO entityRegistrationDTO,
			String regNo) {
		String entityType = entityRegistrationDTO.getEntityType();
		switch (entityType) {
		case EntityRegistrationType.STATE_OWNED:
			GenerationUnitType genTypeForStateOwned = generationUnitTypeRepository.findByShortName("SGS");
			setConditionallyMasterDataBasedOnEntityTypeForGeneratorStateEntity(entityRegistrationDTO, regNo,
					genTypeForStateOwned, entityType);
			break;
		case EntityRegistrationType.IPP:
			GenerationUnitType genTypeForIpp = generationUnitTypeRepository.findByShortName(EntityRegistrationType.IPP);
			setConditionallyMasterDataBasedOnEntityTypeForGeneratorStateEntity(entityRegistrationDTO, regNo,
					genTypeForIpp, entityType);
			break;
		case EntityRegistrationType.CPP:
			GenerationUnitType genTypeForCpp = generationUnitTypeRepository.findByShortName(EntityRegistrationType.CPP);
			setConditionallyMasterDataBasedOnEntityTypeForGeneratorStateEntity(entityRegistrationDTO, regNo,
					genTypeForCpp, entityType);
			break;
		case EntityRegistrationType.CO_GEN:
			GenerationUnitType genTypeForCoGen = generationUnitTypeRepository.findByShortName("CO-GEN");
			setConditionallyMasterDataBasedOnEntityTypeForGeneratorStateEntity(entityRegistrationDTO, regNo,
					genTypeForCoGen, entityType);
			break;
		case EntityRegistrationType.NRSE:
			GenerationUnitType genTypeForNrse = generationUnitTypeRepository.findByShortName("SGS");
			setConditionallyMasterDataBasedOnEntityTypeForGeneratorStateEntity(entityRegistrationDTO, regNo,
					genTypeForNrse, entityType);
			break;
		case EntityRegistrationType.DIST_LICENSEE:
			TypeOfUtilities discomUtilityType = typeOfUtilitiesRepository.findByType(UtilityTypeUtil.DISCOM);
			setConditionallyMasterDataBasedOnEntityTypeForDiscomStateEntity(entityRegistrationDTO, regNo, entityType,
					discomUtilityType);
			break;
		case EntityRegistrationType.DEEM_DIST_LICENSEE:
			TypeOfUtilities deemedDiscomUtilityType = typeOfUtilitiesRepository.findByType(UtilityTypeUtil.DEEM_DISCOM);
			setConditionallyMasterDataBasedOnEntityTypeForDiscomStateEntity(entityRegistrationDTO, regNo, entityType,
					deemedDiscomUtilityType);
			break;
		case EntityRegistrationType.FULL_OA:
			TypeOfUtilities fullOAUtilityType = typeOfUtilitiesRepository.findByType(UtilityTypeUtil.FULL_OA);
			setConditionallyMasterDataBasedOnEntityTypeForOAConsumerStateEntity(entityRegistrationDTO, regNo,
					entityType, fullOAUtilityType);
			break;
		case EntityRegistrationType.PARTIAL_OA:
			TypeOfUtilities partialOAUtilityType = typeOfUtilitiesRepository.findByType(UtilityTypeUtil.PART_OA);
			setConditionallyMasterDataBasedOnEntityTypeForOAConsumerStateEntity(entityRegistrationDTO, regNo,
					entityType, partialOAUtilityType);
			break;
		default:
			break;
		}
	}

	public void setMasterDataForQCAType(EntityRegistrationDTO entityRegistrationDTO, String regNo) {
		String entityRegType = entityRegistrationDTO.getEntityRegType();
		String appNo = entityRegistrationDTO.getAppNo();
		String entityType = entityRegistrationDTO.getEntityType();
		String entityName = entityRegistrationDTO.getEntityName();
		Boolean isStandAloneGenerator = entityRegistrationDTO.getIsStandAloneGenerator();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO contactPerson = entityRegistrationDTO.getContactPerson();
		ContactPersonDTO controlRoomContact = entityRegistrationDTO.getControlRoomContact();
		List<ContactPersonDTO> directors = entityRegistrationDTO.getDirectors();
		Double netWorth = entityRegistrationDTO.getNetWorth();
		BankDetailsDTO bankDetails = entityRegistrationDTO.getBankDetails();

		TypeOfUtilities findByType = typeOfUtilitiesRepository.findByType(UtilityTypeUtil.QCA);
		UtilitiesTraderGenco utilitiesTraderGenco = new UtilitiesTraderGenco();
		utilitiesTraderGenco.setName(entityName);
		utilitiesTraderGenco.setTypeOfUtilities(findByType);
		utilitiesTraderGenco.setIsScheduling(EntityRegistrationStatus.DIS);
		utilitiesTraderGenco.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(utilitiesTraderGenco);

		entityRegistrationDTO.setUtgId(utilitiesTraderGenco.getUID());
		entityRegistrationDTO.setScheduling(EntityRegistrationStatus.DIS);
		entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DIS);

		QCA qcaObj = new QCA();
		qcaObj.setEntityType(entityType);
		qcaObj.setEntityRegType(entityRegType);
		qcaObj.setPlantName(entityName);
		qcaObj.setIsStandAloneGenerator(isStandAloneGenerator);
		qcaObj.setBusinessName(businessName);
		qcaObj.setBusinessAddress(businessAddress);
		qcaObj.setPostalAddress(postalAddress);
		qcaObj.setGstNo(gstNo);
		qcaObj.setPanNo(panNo);
		qcaObj.setContactPerson(contactPerson);
		qcaObj.setControlRoomContact(controlRoomContact);
		qcaObj.setDirectors(directors);
		qcaObj.setNetWorth(netWorth);
		qcaObj.setBankDetails(bankDetails);
		qcaObj.setAppNo(appNo);
		qcaObj.setRegNo(regNo);
		qcaObj.setRegStatus(EntityRegistrationStatus.REG);
		qcaObj.setUtilitiesTraderGenco(utilitiesTraderGenco);
		qcaRepository.createUserData(qcaObj);
		qcaRepository.save(qcaObj);
	}

	public void setMasterDataForQcaPssType(EntityRegistrationDTO entityRegistrationDTO, String regNo) {
		String entityRegType = entityRegistrationDTO.getEntityRegType();
		String energyType = entityRegistrationDTO.getEnergyType();
		String entityType = entityRegistrationDTO.getEntityType();
		String boundary = entityRegistrationDTO.getBoundary();
		Integer pssId = entityRegistrationDTO.getPssDetails().getPssId();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		BankGuaranteeDetailsDTO bankGuaranteeDetails = entityRegistrationDTO.getBankGuaranteeDetails();
		Integer qcaId = entityRegistrationDTO.getQcaId();
		String appNo = entityRegistrationDTO.getAppNo();

		QCA findByQcaId = qcaRepository.findByQcaId(qcaId);
		PSS findByPssId = pssRepository.findByPssId(pssId);
		EnergySource findBySourceName = energySourceRepository.findBySourceName(energyType);

		TypeOfUtilities findByType = typeOfUtilitiesRepository.findByType(UtilityTypeUtil.QCA_PSS);
		UtilitiesTraderGenco utilitiesTraderGenco = new UtilitiesTraderGenco();
		utilitiesTraderGenco.setName(findByQcaId.getPlantName());
		utilitiesTraderGenco.setTypeOfUtilities(findByType);
		utilitiesTraderGenco.setIsScheduling(EntityRegistrationStatus.DIS);
		utilitiesTraderGenco.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(utilitiesTraderGenco);

		entityRegistrationDTO.setUtgId(utilitiesTraderGenco.getUID());
		entityRegistrationDTO.setScheduling(EntityRegistrationStatus.DIS);
		entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DIS);

		QcaPss qcaPssObj = new QcaPss();
		qcaPssObj.setEntityType(entityType);
		qcaPssObj.setEntityRegType(entityRegType);
		qcaPssObj.setEnergySourceDetail(findBySourceName);
		qcaPssObj.setBoundary(boundary);
		qcaPssObj.setPssId(findByPssId);
		qcaPssObj.setQcaUid(findByQcaId);
		qcaPssObj.setRegistrationFeeDetails(registrationFeeDetails);
		qcaPssObj.setBankGuaranteeDetails(bankGuaranteeDetails);
		qcaPssObj.setRegNo(regNo);
		qcaPssObj.setAppNo(appNo);
		qcaPssObj.setRegStatus(EntityRegistrationStatus.REG);
		qcaPssObj.setUtilitiesTraderGenco(utilitiesTraderGenco);
		qcaPssRepository.createUserData(qcaPssObj);
		qcaPssRepository.save(qcaPssObj);
	}

	public void setSchedulingForQcaPss(EntityRegistrationDTO entityRegistrationDTO, String entityRegType) {
		String regNo = entityRegistrationDTO.getRegNo();
		QcaPss findByRegNo = qcaPssRepository.findByRegNo(regNo);
		findByRegNo.setBankGuaranteeDetails(entityRegistrationDTO.getBankGuaranteeDetails());
		qcaPssRepository.save(findByRegNo);

		entityRegistrationDTO.setScheduling(EntityRegistrationStatus.ENB);

		UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository
				.findByUtgId(findByRegNo.getUtilitiesTraderGenco().getUID());
		findByUtgId.setIsScheduling(EntityRegistrationStatus.ENB);
		utilitiesTraderGencoRepository.save(findByUtgId);
	}

	public void setMasterDataForREGType(EntityRegistrationDTO entityRegistrationDTO, String regNo) {
		String entityRegType = entityRegistrationDTO.getEntityRegType();
		String appNo = entityRegistrationDTO.getAppNo();
		String energyType = entityRegistrationDTO.getEnergyType();
		String boundary = entityRegistrationDTO.getBoundary();
		String entityType = entityRegistrationDTO.getEntityType();
		String entityName = entityRegistrationDTO.getEntityName();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO contactPerson = entityRegistrationDTO.getContactPerson();
		List<ContactPersonDTO> directors = entityRegistrationDTO.getDirectors();
		Double netWorth = entityRegistrationDTO.getNetWorth();
		Integer pssId = entityRegistrationDTO.getPssDetails().getPssId();
		REGeneratorDetailsDTO reGeneratorDetails = entityRegistrationDTO.getReGeneratorDetails();
		List<MeteringDetailsDTO> meteringDetails = entityRegistrationDTO.getMeteringDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		GeneratorEntityScadaApprovalDTO scadaData = entityRegistrationDTO.getScadaData();

		TypeOfUtilities findByType = typeOfUtilitiesRepository.findByType(UtilityTypeUtil.GEN);
		UtilitiesTraderGenco utilitiesTraderGenco = new UtilitiesTraderGenco();
		utilitiesTraderGenco.setName(entityName);
		utilitiesTraderGenco.setTypeOfUtilities(findByType);
		utilitiesTraderGenco.setIsScheduling(EntityRegistrationStatus.ENB);
		utilitiesTraderGenco.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(utilitiesTraderGenco);

		entityRegistrationDTO.setUtgId(utilitiesTraderGenco.getUID());
		entityRegistrationDTO.setScheduling(EntityRegistrationStatus.ENB);
		entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DIS);

		Generation<GeneratorDetailsDTOType> generationObj = new Generation<>();
		if (boundary.equals("Intra-State")) {
			GenerationUnitType findByShortName = generationUnitTypeRepository.findByShortName("IPP");
			generationObj.setGenerationTypeDetail(findByShortName);
		} else if (boundary.equals("Inter-State")) {
			GenerationUnitType findByShortName1 = generationUnitTypeRepository.findByShortName("ISGS");
			generationObj.setGenerationTypeDetail(findByShortName1);
		} else if (boundary.equals("Mixed")) {
			GenerationUnitType findByShortName2 = generationUnitTypeRepository.findByShortName("SGS");
			generationObj.setGenerationTypeDetail(findByShortName2);
		}

		EnergySource findBySourceName = energySourceRepository.findBySourceName(energyType);
		generationObj.setEnergySourceDetail(findBySourceName);
		generationObj.setPlantName(entityName);
		generationObj.setEntityType(entityType);
		generationObj.setEntityRegType(entityRegType);
		generationObj.setUtilitiesTraderGenco(utilitiesTraderGenco);
		generationObj.setBoundary(boundary);
		generationObj.setBusinessName(businessName);
		generationObj.setBusinessAddress(businessAddress);
		generationObj.setPostalAddress(postalAddress);
		generationObj.setGstNo(gstNo);
		generationObj.setPanNo(panNo);
		generationObj.setContactPerson(contactPerson);
		generationObj.setDirectors(directors);
		generationObj.setNetWorth(netWorth);
		generationObj.setMeteringDetails(meteringDetails);
		generationObj.setRegistrationFeeDetails(registrationFeeDetails);
		generationObj.setRegNo(regNo);
		generationObj.setAppNo(appNo);
		generationObj.setRegStatus(EntityRegistrationStatus.REG);
		generationObj.setGeneratorDetails(reGeneratorDetails);
		generationObj.setPssId(pssId);
		generationObj.setScadaDataApproved(scadaData);
		generationRepository.createUserData(generationObj);
		generationRepository.save(generationObj);
	}

	public void setConditionallyMasterDataBasedOnEntityTypeForGeneratorStateEntity(
			EntityRegistrationDTO entityRegistrationDTO, String regNo, GenerationUnitType genTypeBasedOnEntityType,
			String entityType) {
		String entityRegType = entityRegistrationDTO.getEntityRegType();
		String appNo = entityRegistrationDTO.getAppNo();
		String entityName = entityRegistrationDTO.getEntityName();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String registeredAddress = entityRegistrationDTO.getRegisteredAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO schedulingContact = entityRegistrationDTO.getSchedulingContact();
		ContactPersonDTO dsmContact = entityRegistrationDTO.getDsmContact();
		LicenseDetailsDTO licenseDetails = entityRegistrationDTO.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = entityRegistrationDTO.getConnectivityDetails();
		GeneratorDetailsDTO generatorDetails = entityRegistrationDTO.getGeneratorDetails();
		Boolean meteringStatus = entityRegistrationDTO.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = entityRegistrationDTO.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = entityRegistrationDTO.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = entityRegistrationDTO.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		String lcLmNo = entityRegistrationDTO.getLcLmNo();
		Date lcLmDate = entityRegistrationDTO.getLcLmDate();
		Double lcLmAmount = entityRegistrationDTO.getLcLmAmount();
		Date lcLmValidUptoDate = entityRegistrationDTO.getLcLmValidUptoDate();
		BankDetailsDTO bankDetails = entityRegistrationDTO.getBankDetails();
		GeneratorEntityScadaApprovalDTO scadaData = entityRegistrationDTO.getScadaData();
		Double totalExBusCapacity = entityRegistrationDTO.getTotalExBusCapacity();

		TypeOfUtilities findByType = typeOfUtilitiesRepository.findByType(UtilityTypeUtil.GEN);
		UtilitiesTraderGenco utilitiesTraderGenco = new UtilitiesTraderGenco();
		utilitiesTraderGenco.setName(entityName);
		utilitiesTraderGenco.setTypeOfUtilities(findByType);
		utilitiesTraderGenco.setIsScheduling(EntityRegistrationStatus.ENB);
		utilitiesTraderGenco.setIsDeRegister(EntityRegistrationStatus.DIS);
		UtilitiesTraderGenco savedUtg = utilitiesTraderGencoRepository.save(utilitiesTraderGenco);

		ExBusCapacity exBusCapacity = new ExBusCapacity();
		exBusCapacity.setUtilitiesTraderGenco(savedUtg);
		exBusCapacity.setExBusCapacity(BigDecimal.valueOf(totalExBusCapacity));
		exBusCapacityRepository.save(exBusCapacity);

		entityRegistrationDTO.setUtgId(utilitiesTraderGenco.getUID());
		entityRegistrationDTO.setScheduling(EntityRegistrationStatus.ENB);
		entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DIS);

		Generation<GeneratorDetailsDTOType> generationObj = new Generation<>();
		generationObj.setGenerationTypeDetail(genTypeBasedOnEntityType);
		if (entityType.equals(EntityRegistrationType.NRSE)) {
			EnergySource sourceNameForNrse = energySourceRepository.findBySourceName("BIOMASS");
			generationObj.setEnergySourceDetail(sourceNameForNrse);
		} else {
			EnergySource sourceNameForOtherThanNrse = energySourceRepository.findBySourceName("THERMAL COAL");
			generationObj.setEnergySourceDetail(sourceNameForOtherThanNrse);
		}
		generationObj.setEntityRegType(entityRegType);
		generationObj.setPlantName(entityName);
		generationObj.setUtilitiesTraderGenco(utilitiesTraderGenco);
		generationObj.setEntityType(entityType);
		generationObj.setRegisteredAddress(registeredAddress);
		generationObj.setSchedulingContact(schedulingContact);
		generationObj.setDsmContact(dsmContact);
		generationObj.setLicenseDetails(licenseDetails);
		generationObj.setConnectivityDetails(connectivityDetails);
		generationObj.setMeteringStatus(meteringStatus);
		generationObj.setOaDetails(oaDetails);
		generationObj.setPpaDetails(ppaDetails);
		generationObj.setLcLmNo(lcLmNo);
		generationObj.setLcLmDate(lcLmDate);
		generationObj.setLcLmAmount(lcLmAmount);
		generationObj.setLcLmValidUptoDate(lcLmValidUptoDate);
		generationObj.setBankDetails(bankDetails);
		generationObj.setBusinessName(businessName);
		generationObj.setBusinessAddress(businessAddress);
		generationObj.setPostalAddress(postalAddress);
		generationObj.setGstNo(gstNo);
		generationObj.setPanNo(panNo);
		generationObj.setMeteringDetails(meteringDetails);
		generationObj.setRegistrationFeeDetails(registrationFeeDetails);
		generationObj.setRegNo(regNo);
		generationObj.setAppNo(appNo);
		generationObj.setRegStatus(EntityRegistrationStatus.REG);
		generationObj.setGeneratorDetails(generatorDetails);
		generationObj.setScadaDataApproved(scadaData);
		generationRepository.createUserData(generationObj);
		generationRepository.save(generationObj);
	}

	public void setConditionallyMasterDataBasedOnEntityTypeForDiscomStateEntity(
			EntityRegistrationDTO entityRegistrationDTO, String regNo, String entityType,
			TypeOfUtilities discomUtilityType) {
		String entityRegType = entityRegistrationDTO.getEntityRegType();
		String appNo = entityRegistrationDTO.getAppNo();
		String entityName = entityRegistrationDTO.getEntityName();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String registeredAddress = entityRegistrationDTO.getRegisteredAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO schedulingContact = entityRegistrationDTO.getSchedulingContact();
		ContactPersonDTO dsmContact = entityRegistrationDTO.getDsmContact();
		LicenseDetailsDTO licenseDetails = entityRegistrationDTO.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = entityRegistrationDTO.getConnectivityDetails();
		Double ncpdQuantum = entityRegistrationDTO.getNcpdQuantum();
		Boolean meteringStatus = entityRegistrationDTO.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = entityRegistrationDTO.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = entityRegistrationDTO.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = entityRegistrationDTO.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		String lcLmNo = entityRegistrationDTO.getLcLmNo();
		Date lcLmDate = entityRegistrationDTO.getLcLmDate();
		Double lcLmAmount = entityRegistrationDTO.getLcLmAmount();
		Date lcLmValidUptoDate = entityRegistrationDTO.getLcLmValidUptoDate();
		BankDetailsDTO bankDetails = entityRegistrationDTO.getBankDetails();
		GeneratorEntityScadaApprovalDTO scadaData = entityRegistrationDTO.getScadaData();
		Double totalExBusCapacity = entityRegistrationDTO.getTotalExBusCapacity();

		UtilitiesTraderGenco utilitiesTraderGenco = new UtilitiesTraderGenco();
		utilitiesTraderGenco.setName(entityName);
		utilitiesTraderGenco.setTypeOfUtilities(discomUtilityType);
		utilitiesTraderGenco.setIsScheduling(EntityRegistrationStatus.ENB);
		utilitiesTraderGenco.setIsDeRegister(EntityRegistrationStatus.DIS);
		UtilitiesTraderGenco savedUtg = utilitiesTraderGencoRepository.save(utilitiesTraderGenco);

		ExBusCapacity exBusCapacity = new ExBusCapacity();
		exBusCapacity.setUtilitiesTraderGenco(savedUtg);
		exBusCapacity.setExBusCapacity(BigDecimal.valueOf(totalExBusCapacity));
		exBusCapacityRepository.save(exBusCapacity);

		entityRegistrationDTO.setUtgId(utilitiesTraderGenco.getUID());
		entityRegistrationDTO.setScheduling(EntityRegistrationStatus.ENB);
		entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DIS);

		Discom discomObj = new Discom();
		discomObj.setDiscomName(entityName);
		discomObj.setEntityType(entityType);
		discomObj.setEntityRegType(entityRegType);
		discomObj.setUtilitiesTraderGenco(utilitiesTraderGenco);
		discomObj.setAppNo(appNo);
		discomObj.setBusinessName(businessName);
		discomObj.setBusinessAddress(businessAddress);
		discomObj.setPostalAddress(postalAddress);
		discomObj.setRegisteredAddress(registeredAddress);
		discomObj.setGstNo(gstNo);
		discomObj.setPanNo(panNo);
		discomObj.setSchedulingContact(schedulingContact);
		discomObj.setDsmContact(dsmContact);
		discomObj.setLicenseDetails(licenseDetails);
		discomObj.setConnectivityDetails(connectivityDetails);
		discomObj.setNcpdQuantum(ncpdQuantum);
		discomObj.setMeteringStatus(meteringStatus);
		discomObj.setMeteringDetails(meteringDetails);
		discomObj.setOaDetails(oaDetails);
		discomObj.setPpaDetails(ppaDetails);
		discomObj.setRegistrationFeeDetails(registrationFeeDetails);
		discomObj.setLcLmNo(lcLmNo);
		discomObj.setLcLmDate(lcLmDate);
		discomObj.setLcLmAmount(lcLmAmount);
		discomObj.setLcLmValidUptoDate(lcLmValidUptoDate);
		discomObj.setBankDetails(bankDetails);
		discomObj.setScadaDataApproved(scadaData);
		discomObj.setRegStatus(EntityRegistrationStatus.REG);
		discomRepository.createUserData(discomObj);
		discomRepository.save(discomObj);
	}

	public void setConditionallyMasterDataBasedOnEntityTypeForOAConsumerStateEntity(
			EntityRegistrationDTO entityRegistrationDTO, String regNo, String entityType,
			TypeOfUtilities oaConsumerUtilityType) {
		String entityRegType = entityRegistrationDTO.getEntityRegType();
		String appNo = entityRegistrationDTO.getAppNo();
		String entityName = entityRegistrationDTO.getEntityName();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String registeredAddress = entityRegistrationDTO.getRegisteredAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO schedulingContact = entityRegistrationDTO.getSchedulingContact();
		ContactPersonDTO dsmContact = entityRegistrationDTO.getDsmContact();
		LicenseDetailsDTO licenseDetails = entityRegistrationDTO.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = entityRegistrationDTO.getConnectivityDetails();
		Double ncpdQuantum = entityRegistrationDTO.getNcpdQuantum();
		Boolean meteringStatus = entityRegistrationDTO.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = entityRegistrationDTO.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = entityRegistrationDTO.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = entityRegistrationDTO.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		String lcLmNo = entityRegistrationDTO.getLcLmNo();
		Date lcLmDate = entityRegistrationDTO.getLcLmDate();
		Double lcLmAmount = entityRegistrationDTO.getLcLmAmount();
		Date lcLmValidUptoDate = entityRegistrationDTO.getLcLmValidUptoDate();
		BankDetailsDTO bankDetails = entityRegistrationDTO.getBankDetails();
		GeneratorEntityScadaApprovalDTO scadaData = entityRegistrationDTO.getScadaData();
		Double totalExBusCapacity = entityRegistrationDTO.getTotalExBusCapacity();

		UtilitiesTraderGenco utilitiesTraderGenco = new UtilitiesTraderGenco();
		utilitiesTraderGenco.setName(entityName);
		utilitiesTraderGenco.setTypeOfUtilities(oaConsumerUtilityType);
		utilitiesTraderGenco.setIsScheduling(EntityRegistrationStatus.ENB);
		utilitiesTraderGenco.setIsDeRegister(EntityRegistrationStatus.DIS);
		UtilitiesTraderGenco savedUtg = utilitiesTraderGencoRepository.save(utilitiesTraderGenco);

		ExBusCapacity exBusCapacity = new ExBusCapacity();
		exBusCapacity.setUtilitiesTraderGenco(savedUtg);
		exBusCapacity.setExBusCapacity(BigDecimal.valueOf(totalExBusCapacity));
		exBusCapacityRepository.save(exBusCapacity);

		entityRegistrationDTO.setUtgId(utilitiesTraderGenco.getUID());
		entityRegistrationDTO.setScheduling(EntityRegistrationStatus.ENB);
		entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DIS);

		Discom discomByShortName = discomRepository.findByShortName("PSPCL");

		OAConsumer oaConsumerObj = new OAConsumer();
		oaConsumerObj.setOaConsumerName(entityName);
		oaConsumerObj.setDiscom(discomByShortName);
		oaConsumerObj.setEntityType(entityType);
		oaConsumerObj.setEntityRegType(entityRegType);
		oaConsumerObj.setUtilitiesTraderGenco(utilitiesTraderGenco);
		oaConsumerObj.setAppNo(appNo);
		oaConsumerObj.setBusinessName(businessName);
		oaConsumerObj.setBusinessAddress(businessAddress);
		oaConsumerObj.setPostalAddress(postalAddress);
		oaConsumerObj.setRegisteredAddress(registeredAddress);
		oaConsumerObj.setGstNo(gstNo);
		oaConsumerObj.setPanNo(panNo);
		oaConsumerObj.setSchedulingContact(schedulingContact);
		oaConsumerObj.setDsmContact(dsmContact);
		oaConsumerObj.setLicenseDetails(licenseDetails);
		oaConsumerObj.setConnectivityDetails(connectivityDetails);
		oaConsumerObj.setNcpdQuantum(ncpdQuantum);
		oaConsumerObj.setMeteringStatus(meteringStatus);
		oaConsumerObj.setMeteringDetails(meteringDetails);
		oaConsumerObj.setOaDetails(oaDetails);
		oaConsumerObj.setPpaDetails(ppaDetails);
		oaConsumerObj.setRegistrationFeeDetails(registrationFeeDetails);
		oaConsumerObj.setLcLmNo(lcLmNo);
		oaConsumerObj.setLcLmDate(lcLmDate);
		oaConsumerObj.setLcLmAmount(lcLmAmount);
		oaConsumerObj.setLcLmValidUptoDate(lcLmValidUptoDate);
		oaConsumerObj.setBankDetails(bankDetails);
		oaConsumerObj.setScadaDataApproved(scadaData);
		oaConsumerObj.setRegStatus(EntityRegistrationStatus.REG);
		oaConsumerRepository.createUserData(oaConsumerObj);
		oaConsumerRepository.save(oaConsumerObj);
	}

	public void checkEntityRegTypeForSendingRevisedDataIntoMasterTables(EntityRegistrationDTO entityRegistrationDTO,
			String draftRegNo, String entityRegType) {
		switch (entityRegType) {
		case EntityRegistrationType.SEN:
			checkEntityTypeForSENRegTypeForSendingRevisedDataIntoMasterTables(entityRegistrationDTO, draftRegNo);
			break;
		case EntityRegistrationType.REG:
			setRevisedMasterDataForREGType(entityRegistrationDTO, draftRegNo);
			break;
		case EntityRegistrationType.QCA:
			setRevisedMasterDataForQCAType(entityRegistrationDTO, draftRegNo);
			break;
		case EntityRegistrationType.QCA_PSS:
			setRevisedMasterDataForQcaPssType(entityRegistrationDTO, draftRegNo);
			break;
		default:
			break;
		}
	}

	private void checkEntityTypeForSENRegTypeForSendingRevisedDataIntoMasterTables(
			EntityRegistrationDTO entityRegistrationDTO, String draftRegNo) {
		String entityType = entityRegistrationDTO.getEntityType();
		switch (entityType) {
		case EntityRegistrationType.STATE_OWNED:
		case EntityRegistrationType.IPP:
		case EntityRegistrationType.CPP:
		case EntityRegistrationType.CO_GEN:
		case EntityRegistrationType.NRSE:
			setConditionallyRevisedMasterDataBasedOnEntityTypeForGeneratorStateEntity(entityRegistrationDTO,
					draftRegNo);
			break;
		case EntityRegistrationType.DIST_LICENSEE:
		case EntityRegistrationType.DEEM_DIST_LICENSEE:
			setConditionallyRevisedMasterDataBasedOnEntityTypeForDiscomStateEntity(entityRegistrationDTO, draftRegNo);
			break;
		case EntityRegistrationType.FULL_OA:
		case EntityRegistrationType.PARTIAL_OA:
			setConditionallyRevisedMasterDataBasedOnEntityTypeForOAConsumerStateEntity(entityRegistrationDTO,
					draftRegNo);
			break;
		default:
			break;
		}
	}

	private void setRevisedMasterDataForQcaPssType(EntityRegistrationDTO entityRegistrationDTO, String draftRegNo) {
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		BankGuaranteeDetailsDTO bankGuaranteeDetails = entityRegistrationDTO.getBankGuaranteeDetails();
		String entityName = entityRegistrationDTO.getEntityName();

		QcaPss findByRegNo = qcaPssRepository.findByRegNo(draftRegNo);

		Integer utgId = findByRegNo.getUtilitiesTraderGenco().getUID();
		UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository.findByUtgId(utgId);
		findByUtgId.setName(entityName);
		findByUtgId.setIsScheduling(EntityRegistrationStatus.ENB);
		findByUtgId.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(findByUtgId);

		findByRegNo.setRegistrationFeeDetails(registrationFeeDetails);
		findByRegNo.setBankGuaranteeDetails(bankGuaranteeDetails);
		findByRegNo.setRegStatus(EntityRegistrationStatus.REG);
		qcaPssRepository.updateUserData(findByRegNo);
		qcaPssRepository.save(findByRegNo);
	}

	private void setRevisedMasterDataForQCAType(EntityRegistrationDTO entityRegistrationDTO, String draftRegNo) {
		String entityName = entityRegistrationDTO.getEntityName();
		Boolean isStandAloneGenerator = entityRegistrationDTO.getIsStandAloneGenerator();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO contactPerson = entityRegistrationDTO.getContactPerson();
		ContactPersonDTO controlRoomContact = entityRegistrationDTO.getControlRoomContact();
		List<ContactPersonDTO> directors = entityRegistrationDTO.getDirectors();
		Double netWorth = entityRegistrationDTO.getNetWorth();
		BankDetailsDTO bankDetails = entityRegistrationDTO.getBankDetails();

		QCA findByRegNo = qcaRepository.findByRegNo(draftRegNo);

		Integer utgId = findByRegNo.getUtilitiesTraderGenco().getUID();
		UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository.findByUtgId(utgId);
		findByUtgId.setName(entityName);
		findByUtgId.setIsScheduling(EntityRegistrationStatus.DIS);
		findByUtgId.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(findByUtgId);

		findByRegNo.setPlantName(entityName);
		findByRegNo.setIsStandAloneGenerator(isStandAloneGenerator);
		findByRegNo.setBusinessName(businessName);
		findByRegNo.setBusinessAddress(businessAddress);
		findByRegNo.setPostalAddress(postalAddress);
		findByRegNo.setGstNo(gstNo);
		findByRegNo.setPanNo(panNo);
		findByRegNo.setContactPerson(contactPerson);
		findByRegNo.setControlRoomContact(controlRoomContact);
		findByRegNo.setDirectors(directors);
		findByRegNo.setNetWorth(netWorth);
		findByRegNo.setBankDetails(bankDetails);
		findByRegNo.setRegStatus(EntityRegistrationStatus.REG);
		qcaRepository.updateUserData(findByRegNo);
		qcaRepository.save(findByRegNo);
	}

	private void setRevisedMasterDataForREGType(EntityRegistrationDTO entityRegistrationDTO, String draftRegNo) {
		String energyType = entityRegistrationDTO.getEnergyType();
		String boundary = entityRegistrationDTO.getBoundary();
		String entityName = entityRegistrationDTO.getEntityName();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO contactPerson = entityRegistrationDTO.getContactPerson();
		List<ContactPersonDTO> directors = entityRegistrationDTO.getDirectors();
		Double netWorth = entityRegistrationDTO.getNetWorth();
		Integer pssId = entityRegistrationDTO.getPssDetails().getPssId();
		REGeneratorDetailsDTO reGeneratorDetails = entityRegistrationDTO.getReGeneratorDetails();
		List<MeteringDetailsDTO> meteringDetails = entityRegistrationDTO.getMeteringDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		GeneratorEntityScadaApprovalDTO scadaData = entityRegistrationDTO.getScadaData();

		Generation<GeneratorDetailsDTOType> findByRegNo = generationRepository.findByRegNo(draftRegNo);

		Integer utgId = findByRegNo.getUtilitiesTraderGenco().getUID();
		UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository.findByUtgId(utgId);
		findByUtgId.setName(entityName);
		findByUtgId.setIsScheduling(EntityRegistrationStatus.ENB);
		findByUtgId.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(findByUtgId);

		if (boundary.equals("Intra-State")) {
			GenerationUnitType findByShortName = generationUnitTypeRepository.findByShortName("IPP");
			findByRegNo.setGenerationTypeDetail(findByShortName);
		} else if (boundary.equals("Inter-State")) {
			GenerationUnitType findByShortName1 = generationUnitTypeRepository.findByShortName("ISGS");
			findByRegNo.setGenerationTypeDetail(findByShortName1);
		} else if (boundary.equals("Mixed")) {
			GenerationUnitType findByShortName2 = generationUnitTypeRepository.findByShortName("SGS");
			findByRegNo.setGenerationTypeDetail(findByShortName2);
		}

		EnergySource findBySourceName = energySourceRepository.findBySourceName(energyType);
		findByRegNo.setEnergySourceDetail(findBySourceName);
		findByRegNo.setPlantName(entityName);
		findByRegNo.setBoundary(boundary);
		findByRegNo.setBusinessName(businessName);
		findByRegNo.setBusinessAddress(businessAddress);
		findByRegNo.setPostalAddress(postalAddress);
		findByRegNo.setGstNo(gstNo);
		findByRegNo.setPanNo(panNo);
		findByRegNo.setContactPerson(contactPerson);
		findByRegNo.setDirectors(directors);
		findByRegNo.setNetWorth(netWorth);
		findByRegNo.setMeteringDetails(meteringDetails);
		findByRegNo.setRegistrationFeeDetails(registrationFeeDetails);
		findByRegNo.setRegStatus(EntityRegistrationStatus.REG);
		findByRegNo.setGeneratorDetails(reGeneratorDetails);
		findByRegNo.setPssId(pssId);
		findByRegNo.setScadaDataApproved(scadaData);
		generationRepository.updateUserData(findByRegNo);
		generationRepository.save(findByRegNo);
	}

	private void setConditionallyRevisedMasterDataBasedOnEntityTypeForOAConsumerStateEntity(
			EntityRegistrationDTO entityRegistrationDTO, String draftRegNo) {
		String entityName = entityRegistrationDTO.getEntityName();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String registeredAddress = entityRegistrationDTO.getRegisteredAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO schedulingContact = entityRegistrationDTO.getSchedulingContact();
		ContactPersonDTO dsmContact = entityRegistrationDTO.getDsmContact();
		LicenseDetailsDTO licenseDetails = entityRegistrationDTO.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = entityRegistrationDTO.getConnectivityDetails();
		Double ncpdQuantum = entityRegistrationDTO.getNcpdQuantum();
		Boolean meteringStatus = entityRegistrationDTO.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = entityRegistrationDTO.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = entityRegistrationDTO.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = entityRegistrationDTO.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		String lcLmNo = entityRegistrationDTO.getLcLmNo();
		Date lcLmDate = entityRegistrationDTO.getLcLmDate();
		Double lcLmAmount = entityRegistrationDTO.getLcLmAmount();
		Date lcLmValidUptoDate = entityRegistrationDTO.getLcLmValidUptoDate();
		BankDetailsDTO bankDetails = entityRegistrationDTO.getBankDetails();
		GeneratorEntityScadaApprovalDTO scadaData = entityRegistrationDTO.getScadaData();
		Double totalExBusCapacity = entityRegistrationDTO.getTotalExBusCapacity();

		OAConsumer findByRegNo = oaConsumerRepository.findByRegNo(draftRegNo);

		Integer utgId = findByRegNo.getUtilitiesTraderGenco().getUID();
		UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository.findByUtgId(utgId);
		findByUtgId.setName(entityName);
		findByUtgId.setIsScheduling(EntityRegistrationStatus.ENB);
		findByUtgId.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(findByUtgId);

		ExBusCapacity exBusCapacityDetails = exBusCapacityRepository.findByUtgId(findByRegNo.getUtilitiesTraderGenco());
		exBusCapacityDetails.setExBusCapacity(BigDecimal.valueOf(totalExBusCapacity));
		exBusCapacityRepository.save(exBusCapacityDetails);

		findByRegNo.setOaConsumerName(entityName);
		findByRegNo.setBusinessName(businessName);
		findByRegNo.setBusinessAddress(businessAddress);
		findByRegNo.setPostalAddress(postalAddress);
		findByRegNo.setRegisteredAddress(registeredAddress);
		findByRegNo.setGstNo(gstNo);
		findByRegNo.setPanNo(panNo);
		findByRegNo.setSchedulingContact(schedulingContact);
		findByRegNo.setDsmContact(dsmContact);
		findByRegNo.setLicenseDetails(licenseDetails);
		findByRegNo.setConnectivityDetails(connectivityDetails);
		findByRegNo.setNcpdQuantum(ncpdQuantum);
		findByRegNo.setMeteringStatus(meteringStatus);
		findByRegNo.setMeteringDetails(meteringDetails);
		findByRegNo.setOaDetails(oaDetails);
		findByRegNo.setPpaDetails(ppaDetails);
		findByRegNo.setRegistrationFeeDetails(registrationFeeDetails);
		findByRegNo.setLcLmNo(lcLmNo);
		findByRegNo.setLcLmDate(lcLmDate);
		findByRegNo.setLcLmAmount(lcLmAmount);
		findByRegNo.setLcLmValidUptoDate(lcLmValidUptoDate);
		findByRegNo.setBankDetails(bankDetails);
		findByRegNo.setScadaDataApproved(scadaData);
		findByRegNo.setRegStatus(EntityRegistrationStatus.REG);
		oaConsumerRepository.updateUserData(findByRegNo);
		oaConsumerRepository.save(findByRegNo);
	}

	private void setConditionallyRevisedMasterDataBasedOnEntityTypeForDiscomStateEntity(
			EntityRegistrationDTO entityRegistrationDTO, String draftRegNo) {
		String entityName = entityRegistrationDTO.getEntityName();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String registeredAddress = entityRegistrationDTO.getRegisteredAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO schedulingContact = entityRegistrationDTO.getSchedulingContact();
		ContactPersonDTO dsmContact = entityRegistrationDTO.getDsmContact();
		LicenseDetailsDTO licenseDetails = entityRegistrationDTO.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = entityRegistrationDTO.getConnectivityDetails();
		Double ncpdQuantum = entityRegistrationDTO.getNcpdQuantum();
		Boolean meteringStatus = entityRegistrationDTO.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = entityRegistrationDTO.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = entityRegistrationDTO.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = entityRegistrationDTO.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		String lcLmNo = entityRegistrationDTO.getLcLmNo();
		Date lcLmDate = entityRegistrationDTO.getLcLmDate();
		Double lcLmAmount = entityRegistrationDTO.getLcLmAmount();
		Date lcLmValidUptoDate = entityRegistrationDTO.getLcLmValidUptoDate();
		BankDetailsDTO bankDetails = entityRegistrationDTO.getBankDetails();
		GeneratorEntityScadaApprovalDTO scadaData = entityRegistrationDTO.getScadaData();
		Double totalExBusCapacity = entityRegistrationDTO.getTotalExBusCapacity();

		Discom findByRegNo = discomRepository.findByRegNo(draftRegNo);

		Integer utgId = findByRegNo.getUtilitiesTraderGenco().getUID();
		UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository.findByUtgId(utgId);
		findByUtgId.setName(entityName);
		findByUtgId.setIsScheduling(EntityRegistrationStatus.ENB);
		findByUtgId.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(findByUtgId);

		ExBusCapacity exBusCapacityDetails = exBusCapacityRepository.findByUtgId(findByRegNo.getUtilitiesTraderGenco());
		exBusCapacityDetails.setExBusCapacity(BigDecimal.valueOf(totalExBusCapacity));
		exBusCapacityRepository.save(exBusCapacityDetails);

		findByRegNo.setDiscomName(entityName);
		findByRegNo.setBusinessName(businessName);
		findByRegNo.setBusinessAddress(businessAddress);
		findByRegNo.setPostalAddress(postalAddress);
		findByRegNo.setRegisteredAddress(registeredAddress);
		findByRegNo.setGstNo(gstNo);
		findByRegNo.setPanNo(panNo);
		findByRegNo.setSchedulingContact(schedulingContact);
		findByRegNo.setDsmContact(dsmContact);
		findByRegNo.setLicenseDetails(licenseDetails);
		findByRegNo.setConnectivityDetails(connectivityDetails);
		findByRegNo.setNcpdQuantum(ncpdQuantum);
		findByRegNo.setMeteringStatus(meteringStatus);
		findByRegNo.setMeteringDetails(meteringDetails);
		findByRegNo.setOaDetails(oaDetails);
		findByRegNo.setPpaDetails(ppaDetails);
		findByRegNo.setRegistrationFeeDetails(registrationFeeDetails);
		findByRegNo.setLcLmNo(lcLmNo);
		findByRegNo.setLcLmDate(lcLmDate);
		findByRegNo.setLcLmAmount(lcLmAmount);
		findByRegNo.setLcLmValidUptoDate(lcLmValidUptoDate);
		findByRegNo.setBankDetails(bankDetails);
		findByRegNo.setScadaDataApproved(scadaData);
		findByRegNo.setRegStatus(EntityRegistrationStatus.REG);
		discomRepository.updateUserData(findByRegNo);
		discomRepository.save(findByRegNo);
	}

	private void setConditionallyRevisedMasterDataBasedOnEntityTypeForGeneratorStateEntity(
			EntityRegistrationDTO entityRegistrationDTO, String draftRegNo) {
		String entityName = entityRegistrationDTO.getEntityName();
		String businessName = entityRegistrationDTO.getBusinessName();
		BusinessAddressDTO businessAddress = entityRegistrationDTO.getBusinessAddress();
		String postalAddress = entityRegistrationDTO.getPostalAddress();
		String registeredAddress = entityRegistrationDTO.getRegisteredAddress();
		String gstNo = entityRegistrationDTO.getGstNo();
		String panNo = entityRegistrationDTO.getPanNo();
		ContactPersonDTO schedulingContact = entityRegistrationDTO.getSchedulingContact();
		ContactPersonDTO dsmContact = entityRegistrationDTO.getDsmContact();
		LicenseDetailsDTO licenseDetails = entityRegistrationDTO.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = entityRegistrationDTO.getConnectivityDetails();
		GeneratorDetailsDTO generatorDetails = entityRegistrationDTO.getGeneratorDetails();
		Boolean meteringStatus = entityRegistrationDTO.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = entityRegistrationDTO.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = entityRegistrationDTO.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = entityRegistrationDTO.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = entityRegistrationDTO.getRegistrationFeeDetails();
		String lcLmNo = entityRegistrationDTO.getLcLmNo();
		Date lcLmDate = entityRegistrationDTO.getLcLmDate();
		Double lcLmAmount = entityRegistrationDTO.getLcLmAmount();
		Date lcLmValidUptoDate = entityRegistrationDTO.getLcLmValidUptoDate();
		BankDetailsDTO bankDetails = entityRegistrationDTO.getBankDetails();
		GeneratorEntityScadaApprovalDTO scadaData = entityRegistrationDTO.getScadaData();
		Double totalExBusCapacity = entityRegistrationDTO.getTotalExBusCapacity();

		Generation<GeneratorDetailsDTOType> findByRegNo = generationRepository.findByRegNo(draftRegNo);

		Integer utgId = findByRegNo.getUtilitiesTraderGenco().getUID();
		UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository.findByUtgId(utgId);
		findByUtgId.setName(entityName);
		findByUtgId.setIsScheduling(EntityRegistrationStatus.ENB);
		findByUtgId.setIsDeRegister(EntityRegistrationStatus.DIS);
		utilitiesTraderGencoRepository.save(findByUtgId);

		ExBusCapacity exBusCapacityDetails = exBusCapacityRepository.findByUtgId(findByRegNo.getUtilitiesTraderGenco());
		exBusCapacityDetails.setExBusCapacity(BigDecimal.valueOf(totalExBusCapacity));
		exBusCapacityRepository.save(exBusCapacityDetails);

		findByRegNo.setPlantName(entityName);
		findByRegNo.setRegisteredAddress(registeredAddress);
		findByRegNo.setSchedulingContact(schedulingContact);
		findByRegNo.setDsmContact(dsmContact);
		findByRegNo.setLicenseDetails(licenseDetails);
		findByRegNo.setConnectivityDetails(connectivityDetails);
		findByRegNo.setMeteringStatus(meteringStatus);
		findByRegNo.setOaDetails(oaDetails);
		findByRegNo.setPpaDetails(ppaDetails);
		findByRegNo.setLcLmNo(lcLmNo);
		findByRegNo.setLcLmDate(lcLmDate);
		findByRegNo.setLcLmAmount(lcLmAmount);
		findByRegNo.setLcLmValidUptoDate(lcLmValidUptoDate);
		findByRegNo.setBankDetails(bankDetails);
		findByRegNo.setBusinessName(businessName);
		findByRegNo.setBusinessAddress(businessAddress);
		findByRegNo.setPostalAddress(postalAddress);
		findByRegNo.setGstNo(gstNo);
		findByRegNo.setPanNo(panNo);
		findByRegNo.setMeteringDetails(meteringDetails);
		findByRegNo.setRegistrationFeeDetails(registrationFeeDetails);
		findByRegNo.setGeneratorDetails(generatorDetails);
		findByRegNo.setScadaDataApproved(scadaData);
		findByRegNo.setRegStatus(EntityRegistrationStatus.REG);
		generationRepository.updateUserData(findByRegNo);
		generationRepository.save(findByRegNo);
	}

	public EntityRegistrationDTO getRegistrationDetailsByRegNo(String regNo, String entityType) {
		EntityRegistrationDTO entityRegistrationDTO = new EntityRegistrationDTO();
		switch (entityType) {
		case EntityRegistrationType.STATE_OWNED:
		case EntityRegistrationType.IPP:
		case EntityRegistrationType.CPP:
		case EntityRegistrationType.CO_GEN:
		case EntityRegistrationType.NRSE:
		case EntityRegistrationType.REG:
			Generation<GeneratorDetailsDTOType> generatorDetails = generationRepository.findByRegNoAndEntityType(regNo,
					entityType);
			entityRegistrationDTO = setEntRegDTOForGenRegDetailsByRegNo(generatorDetails, entityType);
			break;
		case EntityRegistrationType.DIST_LICENSEE:
		case EntityRegistrationType.DEEM_DIST_LICENSEE:
			Discom discomDetails = discomRepository.findByRegNoAndEntityType(regNo, entityType);
			entityRegistrationDTO = setEntRegDTOForDiscomRegDetailsByRegNo(discomDetails, entityType);
			break;
		case EntityRegistrationType.FULL_OA:
		case EntityRegistrationType.PARTIAL_OA:
			OAConsumer oaConsumerDetails = oaConsumerRepository.findByRegNoAndEntityType(regNo, entityType);
			entityRegistrationDTO = setEntRegDTOForOAConsumerRegDetailsByRegNo(oaConsumerDetails, entityType);
			break;
		case EntityRegistrationType.QCA:
			QCA qcaDetails = qcaRepository.findByRegNoAndEntityType(regNo, entityType);
			entityRegistrationDTO = setEntRegDTOForQCARegDetailsByRegNo(qcaDetails, entityType);
			break;
		case EntityRegistrationType.QCA_PSS:
			QcaPss qcaPssDetails = qcaPssRepository.findByRegNoAndEntityType(regNo, entityType);
			entityRegistrationDTO = setEntRegDTOForQcaPssRegDetailsByRegNo(qcaPssDetails, entityType);
			break;
		default:
			break;
		}
		return entityRegistrationDTO;
	}

	public EntityRegistrationDTO setEntRegDTOForGenRegDetailsByRegNo(
			Generation<GeneratorDetailsDTOType> generatorDetails, String entityType) {
		String plantName = generatorDetails.getPlantName();
		String businessName = generatorDetails.getBusinessName();
		BusinessAddressDTO businessAddress = generatorDetails.getBusinessAddress();
		String registeredAddress = generatorDetails.getRegisteredAddress();
		String postalAddress = generatorDetails.getPostalAddress();
		String gstNo = generatorDetails.getGstNo();
		String panNo = generatorDetails.getPanNo();
		ContactPersonDTO schedulingContact = generatorDetails.getSchedulingContact();
		ContactPersonDTO dsmContact = generatorDetails.getDsmContact();
		LicenseDetailsDTO licenseDetails = generatorDetails.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = generatorDetails.getConnectivityDetails();
		Boolean meteringStatus = generatorDetails.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = generatorDetails.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = generatorDetails.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = generatorDetails.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = generatorDetails.getRegistrationFeeDetails();
		String lcLmNo = generatorDetails.getLcLmNo();
		Date lcLmDate = generatorDetails.getLcLmDate();
		BankDetailsDTO bankDetails = generatorDetails.getBankDetails();
		String energyType = generatorDetails.getEnergySourceDetail().getSourceName();
		String boundary = generatorDetails.getBoundary();
		ContactPersonDTO contactPerson = generatorDetails.getContactPerson();
		List<ContactPersonDTO> directors = generatorDetails.getDirectors();
		Double netWorth = generatorDetails.getNetWorth();
		Integer pssId = generatorDetails.getPssId();
		String regNo = generatorDetails.getRegNo();
		String entityRegType = generatorDetails.getEntityRegType();
		String appNo = generatorDetails.getAppNo();

		EntityRegistrationDTO entityRegistrationDTOForGen = new EntityRegistrationDTO();
		entityRegistrationDTOForGen.setEntityType(entityType);
		entityRegistrationDTOForGen.setEntityRegType(entityRegType);
		entityRegistrationDTOForGen.setEntityName(plantName);
		entityRegistrationDTOForGen.setBusinessName(businessName);
		entityRegistrationDTOForGen.setBusinessAddress(businessAddress);
		entityRegistrationDTOForGen.setPostalAddress(postalAddress);
		entityRegistrationDTOForGen.setGstNo(gstNo);
		entityRegistrationDTOForGen.setPanNo(panNo);
		entityRegistrationDTOForGen.setMeteringDetails(meteringDetails);
		entityRegistrationDTOForGen.setRegNo(regNo);
		entityRegistrationDTOForGen.setAppNo(appNo);

		if (entityType.equals(EntityRegistrationType.REG)) {
			entityRegistrationDTOForGen.setEnergyType(energyType);
			entityRegistrationDTOForGen.setBoundary(boundary);
			entityRegistrationDTOForGen.setContactPerson(contactPerson);
			entityRegistrationDTOForGen.setDirectors(directors);
			entityRegistrationDTOForGen.setNetWorth(netWorth);
			entityRegistrationDTOForGen.setRegistrationFeeDetails(registrationFeeDetails);
			PSS findByPssId = pssRepository.findByPssId(pssId);
			PSSDetailsDTO pssDetailsDTO = new PSSDetailsDTO();
			pssDetailsDTO.setPssId(pssId);
			pssDetailsDTO.setPssName(findByPssId.getPssName());
			pssDetailsDTO.setPssAddress(findByPssId.getPssAddress());
			pssDetailsDTO.setTotalInstalledCapacity(findByPssId.getTotalInstalledCapacity());
			pssDetailsDTO.setVoltageLevel(findByPssId.getVoltageLevel());
			pssDetailsDTO.setGridSubstation(findByPssId.getGridSubstation());
			entityRegistrationDTOForGen.setPssDetails(pssDetailsDTO);
			REGeneratorDetailsDTO reGeneratorDetailsDTO = (REGeneratorDetailsDTO) objectMapper
					.convertValue(generatorDetails.getGeneratorDetails(), REGeneratorDetailsDTO.class);
			entityRegistrationDTOForGen.setReGeneratorDetails(reGeneratorDetailsDTO);
		} else {
			entityRegistrationDTOForGen.setRegisteredAddress(registeredAddress);
			entityRegistrationDTOForGen.setSchedulingContact(schedulingContact);
			entityRegistrationDTOForGen.setDsmContact(dsmContact);
			entityRegistrationDTOForGen.setLicenseDetails(licenseDetails);
			entityRegistrationDTOForGen.setConnectivityDetails(connectivityDetails);
			GeneratorDetailsDTO generatorDetailsDTO = objectMapper.convertValue(generatorDetails.getGeneratorDetails(),
					GeneratorDetailsDTO.class);
			entityRegistrationDTOForGen.setGeneratorDetails(generatorDetailsDTO);
			entityRegistrationDTOForGen.setMeteringStatus(meteringStatus);
			entityRegistrationDTOForGen.setOaDetails(oaDetails);
			entityRegistrationDTOForGen.setPpaDetails(ppaDetails);
			entityRegistrationDTOForGen.setLcLmNo(lcLmNo);
			entityRegistrationDTOForGen.setLcLmDate(lcLmDate);
			entityRegistrationDTOForGen.setBankDetails(bankDetails);
		}
		return entityRegistrationDTOForGen;
	}

	public EntityRegistrationDTO setEntRegDTOForDiscomRegDetailsByRegNo(Discom discomDetails, String entityType) {
		String discomName = discomDetails.getDiscomName();
		String businessName = discomDetails.getBusinessName();
		BusinessAddressDTO businessAddress = discomDetails.getBusinessAddress();
		String registeredAddress = discomDetails.getRegisteredAddress();
		String postalAddress = discomDetails.getPostalAddress();
		String gstNo = discomDetails.getGstNo();
		String panNo = discomDetails.getPanNo();
		ContactPersonDTO schedulingContact = discomDetails.getSchedulingContact();
		ContactPersonDTO dsmContact = discomDetails.getDsmContact();
		Double ncpdQuantum = discomDetails.getNcpdQuantum();
		LicenseDetailsDTO licenseDetails = discomDetails.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = discomDetails.getConnectivityDetails();
		Boolean meteringStatus = discomDetails.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = discomDetails.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = discomDetails.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = discomDetails.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = discomDetails.getRegistrationFeeDetails();
		String lcLmNo = discomDetails.getLcLmNo();
		Date lcLmDate = discomDetails.getLcLmDate();
		BankDetailsDTO bankDetails = discomDetails.getBankDetails();
		String regNo = discomDetails.getRegNo();
		String entityRegType = discomDetails.getEntityRegType();
		String appNo = discomDetails.getAppNo();

		EntityRegistrationDTO entityRegistrationDTOForDiscom = new EntityRegistrationDTO();
		entityRegistrationDTOForDiscom.setEntityRegType(entityRegType);
		entityRegistrationDTOForDiscom.setEntityType(entityType);
		entityRegistrationDTOForDiscom.setEntityName(discomName);
		entityRegistrationDTOForDiscom.setBusinessName(businessName);
		entityRegistrationDTOForDiscom.setBusinessAddress(businessAddress);
		entityRegistrationDTOForDiscom.setRegisteredAddress(registeredAddress);
		entityRegistrationDTOForDiscom.setPostalAddress(postalAddress);
		entityRegistrationDTOForDiscom.setGstNo(gstNo);
		entityRegistrationDTOForDiscom.setPanNo(panNo);
		entityRegistrationDTOForDiscom.setSchedulingContact(schedulingContact);
		entityRegistrationDTOForDiscom.setDsmContact(dsmContact);
		entityRegistrationDTOForDiscom.setNcpdQuantum(ncpdQuantum);
		entityRegistrationDTOForDiscom.setLicenseDetails(licenseDetails);
		entityRegistrationDTOForDiscom.setConnectivityDetails(connectivityDetails);
		entityRegistrationDTOForDiscom.setMeteringStatus(meteringStatus);
		entityRegistrationDTOForDiscom.setMeteringDetails(meteringDetails);
		entityRegistrationDTOForDiscom.setOaDetails(oaDetails);
		entityRegistrationDTOForDiscom.setPpaDetails(ppaDetails);
		entityRegistrationDTOForDiscom.setRegistrationFeeDetails(registrationFeeDetails);
		entityRegistrationDTOForDiscom.setLcLmNo(lcLmNo);
		entityRegistrationDTOForDiscom.setLcLmDate(lcLmDate);
		entityRegistrationDTOForDiscom.setBankDetails(bankDetails);
		entityRegistrationDTOForDiscom.setRegNo(regNo);
		entityRegistrationDTOForDiscom.setAppNo(appNo);
		return entityRegistrationDTOForDiscom;
	}

	public EntityRegistrationDTO setEntRegDTOForOAConsumerRegDetailsByRegNo(OAConsumer oaConsumerDetails,
			String entityType) {
		String oaConsumerName = oaConsumerDetails.getOaConsumerName();
		String businessName = oaConsumerDetails.getBusinessName();
		BusinessAddressDTO businessAddress = oaConsumerDetails.getBusinessAddress();
		String registeredAddress = oaConsumerDetails.getRegisteredAddress();
		String postalAddress = oaConsumerDetails.getPostalAddress();
		String gstNo = oaConsumerDetails.getGstNo();
		String panNo = oaConsumerDetails.getPanNo();
		ContactPersonDTO schedulingContact = oaConsumerDetails.getSchedulingContact();
		ContactPersonDTO dsmContact = oaConsumerDetails.getDsmContact();
		Double ncpdQuantum = oaConsumerDetails.getNcpdQuantum();
		LicenseDetailsDTO licenseDetails = oaConsumerDetails.getLicenseDetails();
		ConnectivityDetailsDTO connectivityDetails = oaConsumerDetails.getConnectivityDetails();
		Boolean meteringStatus = oaConsumerDetails.getMeteringStatus();
		List<MeteringDetailsDTO> meteringDetails = oaConsumerDetails.getMeteringDetails();
		List<OaPpaDetailsDTO> oaDetails = oaConsumerDetails.getOaDetails();
		List<OaPpaDetailsDTO> ppaDetails = oaConsumerDetails.getPpaDetails();
		RegistrationFeeDetailsDTO registrationFeeDetails = oaConsumerDetails.getRegistrationFeeDetails();
		String lcLmNo = oaConsumerDetails.getLcLmNo();
		Date lcLmDate = oaConsumerDetails.getLcLmDate();
		BankDetailsDTO bankDetails = oaConsumerDetails.getBankDetails();
		String regNo = oaConsumerDetails.getRegNo();
		String entityRegType = oaConsumerDetails.getEntityRegType();
		String appNo = oaConsumerDetails.getAppNo();

		EntityRegistrationDTO entityRegistrationDTOForOAConsumer = new EntityRegistrationDTO();
		entityRegistrationDTOForOAConsumer.setEntityRegType(entityRegType);
		entityRegistrationDTOForOAConsumer.setEntityType(entityType);
		entityRegistrationDTOForOAConsumer.setEntityName(oaConsumerName);
		entityRegistrationDTOForOAConsumer.setBusinessName(businessName);
		entityRegistrationDTOForOAConsumer.setBusinessAddress(businessAddress);
		entityRegistrationDTOForOAConsumer.setRegisteredAddress(registeredAddress);
		entityRegistrationDTOForOAConsumer.setPostalAddress(postalAddress);
		entityRegistrationDTOForOAConsumer.setGstNo(gstNo);
		entityRegistrationDTOForOAConsumer.setPanNo(panNo);
		entityRegistrationDTOForOAConsumer.setSchedulingContact(schedulingContact);
		entityRegistrationDTOForOAConsumer.setDsmContact(dsmContact);
		entityRegistrationDTOForOAConsumer.setNcpdQuantum(ncpdQuantum);
		entityRegistrationDTOForOAConsumer.setLicenseDetails(licenseDetails);
		entityRegistrationDTOForOAConsumer.setConnectivityDetails(connectivityDetails);
		entityRegistrationDTOForOAConsumer.setMeteringStatus(meteringStatus);
		entityRegistrationDTOForOAConsumer.setMeteringDetails(meteringDetails);
		entityRegistrationDTOForOAConsumer.setOaDetails(oaDetails);
		entityRegistrationDTOForOAConsumer.setPpaDetails(ppaDetails);
		entityRegistrationDTOForOAConsumer.setRegistrationFeeDetails(registrationFeeDetails);
		entityRegistrationDTOForOAConsumer.setLcLmNo(lcLmNo);
		entityRegistrationDTOForOAConsumer.setLcLmDate(lcLmDate);
		entityRegistrationDTOForOAConsumer.setBankDetails(bankDetails);
		entityRegistrationDTOForOAConsumer.setRegNo(regNo);
		entityRegistrationDTOForOAConsumer.setAppNo(appNo);
		return entityRegistrationDTOForOAConsumer;
	}

	public EntityRegistrationDTO setEntRegDTOForQCARegDetailsByRegNo(QCA qcaDetails, String entityType) {
		Boolean isStandAloneGenerator = qcaDetails.getIsStandAloneGenerator();
		String plantName = qcaDetails.getPlantName();
		String businessName = qcaDetails.getBusinessName();
		BusinessAddressDTO businessAddress = qcaDetails.getBusinessAddress();
		String postalAddress = qcaDetails.getPostalAddress();
		String gstNo = qcaDetails.getGstNo();
		String panNo = qcaDetails.getPanNo();
		ContactPersonDTO contactPerson = qcaDetails.getContactPerson();
		ContactPersonDTO controlRoomContact = qcaDetails.getControlRoomContact();
		List<ContactPersonDTO> directors = qcaDetails.getDirectors();
		Double netWorth = qcaDetails.getNetWorth();
		BankDetailsDTO bankDetails = qcaDetails.getBankDetails();
		String regNo = qcaDetails.getRegNo();
		String entityRegType = qcaDetails.getEntityRegType();
		String appNo = qcaDetails.getAppNo();

		EntityRegistrationDTO entityRegistrationDTOForQCA = new EntityRegistrationDTO();
		entityRegistrationDTOForQCA.setEntityRegType(entityRegType);
		entityRegistrationDTOForQCA.setEntityType(entityType);
		entityRegistrationDTOForQCA.setIsStandAloneGenerator(isStandAloneGenerator);
		entityRegistrationDTOForQCA.setEntityName(plantName);
		entityRegistrationDTOForQCA.setBusinessName(businessName);
		entityRegistrationDTOForQCA.setBusinessAddress(businessAddress);
		entityRegistrationDTOForQCA.setPostalAddress(postalAddress);
		entityRegistrationDTOForQCA.setGstNo(gstNo);
		entityRegistrationDTOForQCA.setPanNo(panNo);
		entityRegistrationDTOForQCA.setContactPerson(contactPerson);
		entityRegistrationDTOForQCA.setControlRoomContact(controlRoomContact);
		entityRegistrationDTOForQCA.setDirectors(directors);
		entityRegistrationDTOForQCA.setNetWorth(netWorth);
		entityRegistrationDTOForQCA.setBankDetails(bankDetails);
		entityRegistrationDTOForQCA.setRegNo(regNo);
		entityRegistrationDTOForQCA.setAppNo(appNo);
		return entityRegistrationDTOForQCA;
	}

	public EntityRegistrationDTO setEntRegDTOForQcaPssRegDetailsByRegNo(QcaPss qcaPssDetails, String entityType) {
		List<REGeneratorDetailsDTO> reGeneratorDetailsDTOList = new ArrayList<>();
		String energyType = qcaPssDetails.getEnergySourceDetail().getSourceName();
		String boundary = qcaPssDetails.getBoundary();
		PSS pssDetails = qcaPssDetails.getPssId();
		RegistrationFeeDetailsDTO registrationFeeDetails = qcaPssDetails.getRegistrationFeeDetails();
		BankGuaranteeDetailsDTO bankGuaranteeDetails = qcaPssDetails.getBankGuaranteeDetails();
		String regNo = qcaPssDetails.getRegNo();
		String entityRegType = qcaPssDetails.getEntityRegType();
		String appNo = qcaPssDetails.getAppNo();

		List<Generation<GeneratorDetailsDTOType>> genDetailsList = generationRepository
				.findByPssId(pssDetails.getPssId());
		if (CollectionUtils.isNotEmpty(genDetailsList)) {
			genDetailsList.forEach(bean -> {
				REGeneratorDetailsDTO reGeneratorDetailsDTO = (REGeneratorDetailsDTO) objectMapper
						.convertValue(bean.getGeneratorDetails(), REGeneratorDetailsDTO.class);
				reGeneratorDetailsDTOList.add(reGeneratorDetailsDTO);
			});
		}

		PSSDetailsDTO pssDetailsDTO = new PSSDetailsDTO();
		pssDetailsDTO.setPssId(pssDetails.getPssId());
		pssDetailsDTO.setPssName(pssDetails.getPssName());
		pssDetailsDTO.setPssAddress(pssDetails.getPssAddress());
		pssDetailsDTO.setTotalInstalledCapacity(pssDetails.getTotalInstalledCapacity());
		pssDetailsDTO.setVoltageLevel(pssDetails.getVoltageLevel());
		pssDetailsDTO.setGridSubstation(pssDetails.getGridSubstation());

		EntityRegistrationDTO entityRegistrationDTOForQcaPss = new EntityRegistrationDTO();
		entityRegistrationDTOForQcaPss.setEntityRegType(entityRegType);
		entityRegistrationDTOForQcaPss.setEntityType(entityType);
		entityRegistrationDTOForQcaPss.setEnergyType(energyType);
		entityRegistrationDTOForQcaPss.setBoundary(boundary);
		entityRegistrationDTOForQcaPss.setPssDetails(pssDetailsDTO);
		entityRegistrationDTOForQcaPss.setReGeneratorDetailsDTOList(reGeneratorDetailsDTOList);
		entityRegistrationDTOForQcaPss.setRegistrationFeeDetails(registrationFeeDetails);
		entityRegistrationDTOForQcaPss.setBankGuaranteeDetails(bankGuaranteeDetails);
		entityRegistrationDTOForQcaPss.setRegNo(regNo);
		entityRegistrationDTOForQcaPss.setAppNo(appNo);
		return entityRegistrationDTOForQcaPss;
	}

	public String setSchedulingOnOrOff(SchedulingOnOffDTO schedulingOnOffDTO) {
		Integer draftId = schedulingOnOffDTO.getDraftId();
		String isScheduling = schedulingOnOffDTO.getIsScheduling();
		String remarks = schedulingOnOffDTO.getRemarks();

		Draft<EntityRegistrationDTO> findDraftById = draftRepository.findDraftById(draftId);
		EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(findDraftById.getData(),
				EntityRegistrationDTO.class);
		String ackNo = entityRegistrationDTO.getAckNo();
		Integer utgId = entityRegistrationDTO.getUtgId();

		entityRegistrationDTO.setScheduling(isScheduling);
		draftRepository.save(findDraftById);

		UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository.findByUtgId(utgId);
		findByUtgId.setIsScheduling(isScheduling);
		utilitiesTraderGencoRepository.save(findByUtgId);

		Workflow workflowObj = workflowRepository.findDepartmentBySectionId("003");
		Remark remarkObj = new Remark();
		remarkObj.setAckNo(ackNo);
		remarkObj.setDepartment(workflowObj.getDepartment());
		remarkObj.setRemarks(remarks);
		remarksRepository.createUserData(remarkObj);
		remarksRepository.save(remarkObj);

		return isScheduling;
	}

	public String deRegisterBySLDC(DeRegisterDTO deRegisterDTO) {
		Integer draftId = deRegisterDTO.getDraftId();
		String remarks = deRegisterDTO.getRemarks();

		Draft<EntityRegistrationDTO> findDraftById = draftRepository.findDraftById(draftId);
		EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(findDraftById.getData(),
				EntityRegistrationDTO.class);
		String ackNo = entityRegistrationDTO.getAckNo();
		String regNo = entityRegistrationDTO.getRegNo();
		Integer utgId = entityRegistrationDTO.getUtgId();
		String entityType = entityRegistrationDTO.getEntityType();
		String returnStr = null;
		Workflow workflowObj = workflowRepository.findDepartmentBySectionId("003");
		switch (entityType) {
		case EntityRegistrationType.STATE_OWNED:
		case EntityRegistrationType.IPP:
		case EntityRegistrationType.CPP:
		case EntityRegistrationType.CO_GEN:
		case EntityRegistrationType.NRSE:
		case EntityRegistrationType.REG:
			entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.ENB);
			entityRegistrationDTO.setScheduling(EntityRegistrationStatus.DIS);
			findDraftById.setData(entityRegistrationDTO);
			findDraftById.setStatus(EntityRegistrationStatus.DE_REG);
			draftRepository.save(findDraftById);

			UtilitiesTraderGenco genUtgDetails = utilitiesTraderGencoRepository.findByUtgId(utgId);
			genUtgDetails.setIsDeRegister(EntityRegistrationStatus.ENB);
			genUtgDetails.setIsScheduling(EntityRegistrationStatus.DIS);
			utilitiesTraderGencoRepository.save(genUtgDetails);

			setRemarks(ackNo, workflowObj.getDepartment(), remarks);

			Generation<GeneratorDetailsDTOType> generatorDetails = generationRepository.findByRegNoAndEntityType(regNo,
					entityType);
			generatorDetails.setRegStatus(EntityRegistrationStatus.DE_REG);
			generationRepository.save(generatorDetails);
			returnStr = entDeRegMsg;
			break;
		case EntityRegistrationType.DIST_LICENSEE:
		case EntityRegistrationType.DEEM_DIST_LICENSEE:
			entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.ENB);
			entityRegistrationDTO.setScheduling(EntityRegistrationStatus.DIS);
			findDraftById.setData(entityRegistrationDTO);
			findDraftById.setStatus(EntityRegistrationStatus.DE_REG);
			draftRepository.save(findDraftById);

			UtilitiesTraderGenco discomUtgDetails = utilitiesTraderGencoRepository.findByUtgId(utgId);
			discomUtgDetails.setIsDeRegister(EntityRegistrationStatus.ENB);
			discomUtgDetails.setIsScheduling(EntityRegistrationStatus.DIS);
			utilitiesTraderGencoRepository.save(discomUtgDetails);

			setRemarks(ackNo, workflowObj.getDepartment(), remarks);

			Discom discomDetails = discomRepository.findByRegNoAndEntityType(regNo, entityType);
			discomDetails.setRegStatus(EntityRegistrationStatus.DE_REG);
			discomRepository.save(discomDetails);
			returnStr = entDeRegMsg;
			break;
		case EntityRegistrationType.FULL_OA:
		case EntityRegistrationType.PARTIAL_OA:
			entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.ENB);
			entityRegistrationDTO.setScheduling(EntityRegistrationStatus.DIS);
			findDraftById.setData(entityRegistrationDTO);
			findDraftById.setStatus(EntityRegistrationStatus.DE_REG);
			draftRepository.save(findDraftById);

			UtilitiesTraderGenco oaConsumerUtgDetails = utilitiesTraderGencoRepository.findByUtgId(utgId);
			oaConsumerUtgDetails.setIsDeRegister(EntityRegistrationStatus.ENB);
			oaConsumerUtgDetails.setIsScheduling(EntityRegistrationStatus.DIS);
			utilitiesTraderGencoRepository.save(oaConsumerUtgDetails);

			setRemarks(ackNo, workflowObj.getDepartment(), remarks);

			OAConsumer oaConsumerDetails = oaConsumerRepository.findByRegNoAndEntityType(regNo, entityType);
			oaConsumerDetails.setRegStatus(EntityRegistrationStatus.DE_REG);
			oaConsumerRepository.save(oaConsumerDetails);
			returnStr = entDeRegMsg;
			break;
		case EntityRegistrationType.QCA:
			QCA qcaDetails = qcaRepository.findByRegNoAndEntityType(regNo, entityType);
			Date qcaInsertTime = qcaDetails.getInsertTime();
			Date currentDateForQCA = new Date();
			Integer countOfDaysForQCA = EPMDateUtils.calculateNumberOfDays(
					EPMDateUtils.getDateWithBeginningTime(qcaInsertTime),
					EPMDateUtils.getDateWithBeginningTime(currentDateForQCA));
			if (countOfDaysForQCA > 730 || countOfDaysForQCA > 731) {
				List<QcaPss> qcaPssList = qcaPssRepository.findByQcaUid(qcaDetails);
				if (CollectionUtils.isEmpty(qcaPssList)) {
					entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.ENB);
					entityRegistrationDTO.setScheduling(EntityRegistrationStatus.DIS);
					findDraftById.setData(entityRegistrationDTO);
					findDraftById.setStatus(EntityRegistrationStatus.DE_REG);
					draftRepository.save(findDraftById);

					UtilitiesTraderGenco qcaUtgDetails = utilitiesTraderGencoRepository.findByUtgId(utgId);
					qcaUtgDetails.setIsDeRegister(EntityRegistrationStatus.ENB);
					qcaUtgDetails.setIsScheduling(EntityRegistrationStatus.DIS);
					utilitiesTraderGencoRepository.save(qcaUtgDetails);

					setRemarks(ackNo, workflowObj.getDepartment(), remarks);

					qcaDetails.setRegStatus(EntityRegistrationStatus.DE_REG);
					qcaRepository.save(qcaDetails);
					returnStr = entDeRegMsg;
				} else {
					returnStr = qcaPssDeLinkErrMsg;
				}
			} else {
				returnStr = qcaDeRegTwoYrsErrMsg;
			}
			break;
		case EntityRegistrationType.QCA_PSS:
			QcaPss qcaPssDetails = qcaPssRepository.findByRegNoAndEntityType(regNo, entityType);
			Date qcaPssInsertTime = qcaPssDetails.getInsertTime();
			Date currentDateForQcaPss = new Date();
			Integer countOfDaysForQcaPss = EPMDateUtils.calculateNumberOfDays(
					EPMDateUtils.getDateWithBeginningTime(qcaPssInsertTime),
					EPMDateUtils.getDateWithBeginningTime(currentDateForQcaPss));
			if (countOfDaysForQcaPss > 730 || countOfDaysForQcaPss > 731) {
				entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.ENB);
				entityRegistrationDTO.setScheduling(EntityRegistrationStatus.DIS);
				findDraftById.setData(entityRegistrationDTO);
				findDraftById.setStatus(EntityRegistrationStatus.DE_REG);
				draftRepository.save(findDraftById);

				UtilitiesTraderGenco qcaPssUtgDetails = utilitiesTraderGencoRepository.findByUtgId(utgId);
				qcaPssUtgDetails.setIsDeRegister(EntityRegistrationStatus.ENB);
				qcaPssUtgDetails.setIsScheduling(EntityRegistrationStatus.DIS);
				utilitiesTraderGencoRepository.save(qcaPssUtgDetails);

				setRemarks(ackNo, workflowObj.getDepartment(), remarks);

				qcaPssDetails.setRegStatus(EntityRegistrationStatus.DE_REG);
				qcaPssRepository.save(qcaPssDetails);
				returnStr = entDeRegMsg;
			} else {
				returnStr = qcaDeRegTwoYrsErrMsg;
			}
			break;
		default:
			break;
		}
		return returnStr;
	}

	public String deRegisterByEntity(String regNo) {
		Draft<EntityRegistrationDTO> findDraftByRegNo = draftRepository.findDraftByRegNo(regNo);
		EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(findDraftByRegNo.getData(),
				EntityRegistrationDTO.class);
		String entityType = entityRegistrationDTO.getEntityType();
		String returnStr = null;
		switch (entityType) {
		case EntityRegistrationType.STATE_OWNED:
		case EntityRegistrationType.IPP:
		case EntityRegistrationType.CPP:
		case EntityRegistrationType.CO_GEN:
		case EntityRegistrationType.NRSE:
		case EntityRegistrationType.REG:
		case EntityRegistrationType.DIST_LICENSEE:
		case EntityRegistrationType.DEEM_DIST_LICENSEE:
		case EntityRegistrationType.FULL_OA:
		case EntityRegistrationType.PARTIAL_OA:
			entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DR_REQ);
			findDraftByRegNo.setData(entityRegistrationDTO);
			draftRepository.save(findDraftByRegNo);
			returnStr = entDeRegRequest;
			break;
		case EntityRegistrationType.QCA:
			QCA qcaDetails = qcaRepository.findByRegNoAndEntityType(regNo, entityType);
			Date qcaInsertTime = qcaDetails.getInsertTime();
			Date currentDateForQCA = new Date();
			Integer countOfDaysForQCA = EPMDateUtils.calculateNumberOfDays(
					EPMDateUtils.getDateWithBeginningTime(qcaInsertTime),
					EPMDateUtils.getDateWithBeginningTime(currentDateForQCA));
			if (countOfDaysForQCA > 730 || countOfDaysForQCA > 731) {
				List<QcaPss> qcaPssList = qcaPssRepository.findByQcaUid(qcaDetails);
				if (CollectionUtils.isEmpty(qcaPssList)) {
					entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DR_REQ);
					findDraftByRegNo.setData(entityRegistrationDTO);
					draftRepository.save(findDraftByRegNo);
					returnStr = entDeRegRequest;
				} else {
					returnStr = qcaPssDeLinkErrMsg;
				}
			} else {
				returnStr = qcaDeRegTwoYrsErrMsg;
			}
			break;
		case EntityRegistrationType.QCA_PSS:
			QcaPss qcaPssDetails = qcaPssRepository.findByRegNoAndEntityType(regNo, entityType);
			Date qcaPssInsertTime = qcaPssDetails.getInsertTime();
			Date currentDateForQcaPss = new Date();
			Integer countOfDaysForQcaPss = EPMDateUtils.calculateNumberOfDays(
					EPMDateUtils.getDateWithBeginningTime(qcaPssInsertTime),
					EPMDateUtils.getDateWithBeginningTime(currentDateForQcaPss));
			if (countOfDaysForQcaPss > 730 || countOfDaysForQcaPss > 731) {
				entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.ENB);
				entityRegistrationDTO.setDeRegister(EntityRegistrationStatus.DR_REQ);
				findDraftByRegNo.setData(entityRegistrationDTO);
				draftRepository.save(findDraftByRegNo);
				returnStr = entDeRegRequest;
			} else {
				returnStr = qcaDeRegTwoYrsErrMsg;
			}
		default:
			break;
		}
		return returnStr;
	}

	public QCADashboardResponseDTO getRegistrationDetailsForQcaDashboard(Integer qcaUtgId) {
		List<QCADashboardSubResponseDTO> qcaDashboardSubResponseDTOList = new ArrayList<>();
		Draft<EntityRegistrationDTO> findDraftByQcaUtgId = draftRepository.findDraftByQcaUtgId(qcaUtgId);
		EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(findDraftByQcaUtgId.getData(),
				EntityRegistrationDTO.class);

		UtilitiesTraderGenco utgDetails = utilitiesTraderGencoRepository.findByUtgId(qcaUtgId);
		QCA findByUtgId = qcaRepository.findByUtgId(utgDetails);

		List<Draft<EntityRegistrationDTO>> findDraftByQcaId = draftRepository.findDraftByQcaId(findByUtgId.getQcaUid());
		if (CollectionUtils.isNotEmpty(findDraftByQcaId)) {
			findDraftByQcaId.forEach(bean -> {
				EntityRegistrationDTO entityRegistrationDTOForSubRespDTO = objectMapper.convertValue(bean.getData(),
						EntityRegistrationDTO.class);

				QCADashboardSubResponseDTO qcaDashboardSubResponseDTO = new QCADashboardSubResponseDTO();
				qcaDashboardSubResponseDTO.setDraftId(bean.getUID());
				qcaDashboardSubResponseDTO.setStatus(bean.getStatus());
				qcaDashboardSubResponseDTO.setRegNo(entityRegistrationDTOForSubRespDTO.getRegNo());
				qcaDashboardSubResponseDTO.setAckNo(entityRegistrationDTOForSubRespDTO.getAckNo());
				qcaDashboardSubResponseDTO.setEntityType(entityRegistrationDTOForSubRespDTO.getEntityType());
				qcaDashboardSubResponseDTO.setEntityName(entityRegistrationDTOForSubRespDTO.getEntityName());
				qcaDashboardSubResponseDTO
						.setGridSubstation(entityRegistrationDTOForSubRespDTO.getPssDetails().getGridSubstation());
				qcaDashboardSubResponseDTO
						.setCapacity(entityRegistrationDTOForSubRespDTO.getPssDetails().getTotalInstalledCapacity());
				qcaDashboardSubResponseDTO
						.setVoltageLevel(entityRegistrationDTOForSubRespDTO.getPssDetails().getVoltageLevel());

				qcaDashboardSubResponseDTOList.add(qcaDashboardSubResponseDTO);
			});
		}

		QCADashboardResponseDTO qcaDashboardResponseDTO = new QCADashboardResponseDTO();
		qcaDashboardResponseDTO.setDraftId(findDraftByQcaUtgId.getUID());
		qcaDashboardResponseDTO.setStatus(findDraftByQcaUtgId.getStatus());
		qcaDashboardResponseDTO.setRegNo(entityRegistrationDTO.getRegNo());
		qcaDashboardResponseDTO.setAckNo(entityRegistrationDTO.getAckNo());
		qcaDashboardResponseDTO.setEntityType(entityRegistrationDTO.getEntityType());
		qcaDashboardResponseDTO.setEntityName(entityRegistrationDTO.getEntityName());
		qcaDashboardResponseDTO.setQcaDashboardSubResponseDTO(qcaDashboardSubResponseDTOList);
		return qcaDashboardResponseDTO;
	}

	public String sendDataWithHeader(String url, String jsonInputString) throws ClientProtocolException, IOException {
		postMethod = new HttpPost(url);
		StringEntity requestEntity = new StringEntity(jsonInputString, ContentType.APPLICATION_JSON);
		postMethod.setEntity(requestEntity);
		postMethod.addHeader("Cookie", selCookie);
		HttpResponse response = httpClient.execute(postMethod);
		int statusCode = response.getStatusLine().getStatusCode();
		HttpEntity entity = response.getEntity();
		return EntityUtils.toString(entity, "utf-8");
	}

	public ReportDashboardResponseDTO getRegistrationDetailsForReportingDashboard(Map<String, Object> requestBody)
			throws ParseException {
		List<String> statusNameList = new ArrayList<>();
		List<RemarkDTO> remarkDTOList = new ArrayList<>();
		List<ReportDashboardSubResponseDTO> draftDetailsList = new ArrayList<>();
		List<String> categoryList = (List<String>) requestBody.get("categoryList");
		if (CollectionUtils.isNotEmpty(categoryList)) {
			List<EntRegStatus> entRegStatusList = queryForFetchingRegStatusByStatusType(categoryList);
			statusNameList = entRegStatusList.stream().map(bean -> bean.getStatusName()).collect(Collectors.toList());
		}
		String fromDateStr = (String) requestBody.get("fromDate");
		String toDateStr = (String) requestBody.get("toDate");
		Date fromDate = null;
		Date toDate = null;
		if (fromDateStr != null && toDateStr != null) {
			fromDate = EPMDateUtils.getDateWithBeginningTime(sdf.parse(fromDateStr));
			toDate = EPMDateUtils.getDateWithEndTime(sdf.parse(toDateStr));
		}

		List<Draft> totalRequestsDetails = queryForFetchingTotalRequestsForReportingDashboard();
		if (CollectionUtils.isNotEmpty(totalRequestsDetails)) {
			ReportDashboardResponseDTO reportDashboardResponseDTO = new ReportDashboardResponseDTO();
			Integer approvedCount = 0;
			Integer rejectedCount = 0;
			Integer pendingCount = 0;
			Integer totalRequestCount = 0;
			for (Draft draft : totalRequestsDetails) {
				if (draft.getStatus().equals(EntityRegistrationStatus.REG)
						|| draft.getStatus().equals(EntityRegistrationStatus.DE_REG)) {
					approvedCount = approvedCount + 1;
				}
				if (draft.getStatus().equals(EntityRegistrationStatus.REJECT)
						|| draft.getStatus().equals(EntityRegistrationStatus.INACTIVE)) {
					rejectedCount = rejectedCount + 1;
				}
				if (draft.getStatus().equals(EntityRegistrationStatus.HOLD)
						|| draft.getStatus().equals(EntityRegistrationStatus.REV)
						|| draft.getStatus().equals(EntityRegistrationStatus.NEW_REG)) {
					pendingCount = pendingCount + 1;
				}
				totalRequestCount++;
			}
			reportDashboardResponseDTO.setTotalRequestCount(totalRequestCount);
			reportDashboardResponseDTO.setApprovedCount(approvedCount);
			reportDashboardResponseDTO.setRejectedCount(rejectedCount);
			reportDashboardResponseDTO.setPendingCount(pendingCount);

			List<Draft> registrationDetails = optionalParamQueryForFetchingDraftDetailsForReportingDashboard(
					statusNameList, fromDate, toDate);
			if (CollectionUtils.isNotEmpty(registrationDetails)) {
				registrationDetails.forEach(bean -> {
					EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(bean.getData(),
							EntityRegistrationDTO.class);
					String ackNo = entityRegistrationDTO.getAckNo();
					String entityType = entityRegistrationDTO.getEntityType();
					Date draftInsertTime = bean.getInsertTime();
					List<Remark> findRemarkByAckNo = remarksRepository.findRemarkByAckNo(ackNo);
					for (Remark remarkObj : findRemarkByAckNo) {
						RemarkDTO remarkDTO = new RemarkDTO();
						remarkDTO.setAckNo(remarkObj.getAckNo());
						remarkDTO.setCreatedDate(remarkObj.getInsertTime());
						remarkDTO.setDepartment(remarkObj.getDepartment());
						remarkDTO.setRemarks(remarkObj.getRemarks());
						remarkDTO.setUserName(remarkObj.getCreatedBy());
						remarkDTOList.add(remarkDTO);
					}

					ReportDashboardSubResponseDTO reportDashboardSubResponseDTO = new ReportDashboardSubResponseDTO();
					reportDashboardSubResponseDTO.setDraftId(bean.getUID());
					reportDashboardSubResponseDTO.setEntityRegType(entityRegistrationDTO.getEntityRegType());
					reportDashboardSubResponseDTO.setStatus(bean.getStatus());
					reportDashboardSubResponseDTO.setRegNo(entityRegistrationDTO.getRegNo());
					reportDashboardSubResponseDTO.setAckNo(ackNo);
					reportDashboardSubResponseDTO.setEntityType(entityType);
					reportDashboardSubResponseDTO.setEntityName(entityRegistrationDTO.getEntityName());
					switch (entityType) {
					case EntityRegistrationType.STATE_OWNED:
					case EntityRegistrationType.IPP:
					case EntityRegistrationType.CPP:
					case EntityRegistrationType.CO_GEN:
					case EntityRegistrationType.NRSE:
						reportDashboardSubResponseDTO.setGridSubstation(
								entityRegistrationDTO.getConnectivityDetails().getInjectionOrDrawlPoint());
						reportDashboardSubResponseDTO
								.setCapacity(entityRegistrationDTO.getGeneratorDetails().getTotalInstalledCapacity());
						reportDashboardSubResponseDTO
								.setVoltage(entityRegistrationDTO.getConnectivityDetails().getVoltageLevel());
						break;
					case EntityRegistrationType.DIST_LICENSEE:
					case EntityRegistrationType.DEEM_DIST_LICENSEE:
					case EntityRegistrationType.FULL_OA:
					case EntityRegistrationType.PARTIAL_OA:
						reportDashboardSubResponseDTO.setGridSubstation(
								entityRegistrationDTO.getConnectivityDetails().getInjectionOrDrawlPoint());
						reportDashboardSubResponseDTO.setCapacity(entityRegistrationDTO.getNcpdQuantum());
						reportDashboardSubResponseDTO
								.setVoltage(entityRegistrationDTO.getConnectivityDetails().getVoltageLevel());
						break;
					case EntityRegistrationType.REG:
						reportDashboardSubResponseDTO
								.setGridSubstation(entityRegistrationDTO.getPssDetails().getGridSubstation());
						reportDashboardSubResponseDTO
								.setCapacity(entityRegistrationDTO.getReGeneratorDetails().getTotalInstalledCapacity());
						reportDashboardSubResponseDTO
								.setVoltage(entityRegistrationDTO.getReGeneratorDetails().getVoltageLevel());
						break;
					case EntityRegistrationType.QCA:
						reportDashboardSubResponseDTO.setGridSubstation("NA");
						reportDashboardSubResponseDTO.setCapacity(0.0);
						reportDashboardSubResponseDTO.setVoltage(0);
						break;
					case EntityRegistrationType.QCA_PSS:
						reportDashboardSubResponseDTO
								.setGridSubstation(entityRegistrationDTO.getPssDetails().getGridSubstation());
						reportDashboardSubResponseDTO
								.setCapacity(entityRegistrationDTO.getPssDetails().getTotalInstalledCapacity());
						reportDashboardSubResponseDTO
								.setVoltage(entityRegistrationDTO.getPssDetails().getVoltageLevel());
						break;
					default:
						break;
					}

					Map<String, WorkflowDTO> workflowMapFromDB = bean.getWorkflow();
					WorkflowDTO workflowDTO = workflowMapFromDB.get("003");
					if (workflowDTO.getStatus().equals(EntityRegistrationStatus.APP)) {
						Date oaDeptApprovalTime = workflowDTO.getInsertTime();
						Integer countOfDaysWithApproval = EPMDateUtils.calculateNumberOfDays(
								EPMDateUtils.getDateWithBeginningTime(draftInsertTime),
								EPMDateUtils.getDateWithBeginningTime(oaDeptApprovalTime));
						reportDashboardSubResponseDTO.setCountOfDays(countOfDaysWithApproval);
						reportDashboardSubResponseDTO.setVerifyDate(oaDeptApprovalTime);
					} else {
						Date currentDate = new Date();

						Integer countOfDaysWithoutApproval = EPMDateUtils.calculateNumberOfDays(
								EPMDateUtils.getDateWithBeginningTime(draftInsertTime),
								EPMDateUtils.getDateWithBeginningTime(currentDate));
						reportDashboardSubResponseDTO.setCountOfDays(countOfDaysWithoutApproval);
						reportDashboardSubResponseDTO.setVerifyDate(null);
					}
					reportDashboardSubResponseDTO.setRegReceiptDate(draftInsertTime);
					reportDashboardSubResponseDTO.setPendingFromDept("NA");
					reportDashboardSubResponseDTO.setRemarkDTOList(remarkDTOList);
					draftDetailsList.add(reportDashboardSubResponseDTO);
				});
				reportDashboardResponseDTO.setDraftDetails(draftDetailsList);
			}
			return reportDashboardResponseDTO;
		} else {
			return null;
		}
	}

	public List<Draft> optionalParamQueryForFetchingDraftDetailsForReportingDashboard(List<String> statusNameList,
			Date fromDate, Date toDate) {
		List<Predicate> predicates = new ArrayList<>();
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Draft> criteriaQuery = criteriaBuilder.createQuery(Draft.class);
		Root<Draft> root = criteriaQuery.from(Draft.class);
		criteriaQuery.select(root);
		Expression<String> parentExpression = root.get("status");
		if (CollectionUtils.isNotEmpty(statusNameList)) {
			Predicate statusPredicate = parentExpression.in(statusNameList);
			predicates.add(statusPredicate);
		} else {
			Predicate statusPredicateDraft = criteriaBuilder.notEqual(root.get("status"),
					EntityRegistrationStatus.DRAFT);
			Predicate statusPredicateCancel = criteriaBuilder.notEqual(root.get("status"),
					EntityRegistrationStatus.CANCELLED);
			Predicate statusPredicateDelete = criteriaBuilder.notEqual(root.get("status"),
					EntityRegistrationStatus.DELETE);
			Predicate draftAndCancelStatusPredicate = criteriaBuilder.and(statusPredicateDraft, statusPredicateCancel,
					statusPredicateDelete);
			predicates.add(draftAndCancelStatusPredicate);
		}

		if (fromDate != null && toDate != null) {
			Predicate datePredicate = criteriaBuilder.between(root.get("insertTime"), fromDate, toDate);
			predicates.add(datePredicate);
		}

		Predicate functionalityAreaPredicate = criteriaBuilder.equal(root.get("functionalityArea"),
				FunctionalityArea.ENT_REG);
		predicates.add(functionalityAreaPredicate);

		Predicate finalPredicate = criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
		criteriaQuery.where(finalPredicate);
		List<Draft> registrationDetails = entityManager.createQuery(criteriaQuery).getResultList();
		return registrationDetails;
	}

	public List<Draft> queryForFetchingTotalRequestsForReportingDashboard() {
		List<Predicate> predicates = new ArrayList<>();
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Draft> criteriaQuery = criteriaBuilder.createQuery(Draft.class);
		Root<Draft> root = criteriaQuery.from(Draft.class);
		criteriaQuery.select(root);

		Predicate statusPredicateDraft = criteriaBuilder.notEqual(root.get("status"), EntityRegistrationStatus.DRAFT);
		Predicate statusPredicateCancel = criteriaBuilder.notEqual(root.get("status"),
				EntityRegistrationStatus.CANCELLED);
		Predicate statusPredicateDelete = criteriaBuilder.notEqual(root.get("status"), EntityRegistrationStatus.DELETE);
		Predicate draftAndCancelStatusPredicate = criteriaBuilder.and(statusPredicateDraft, statusPredicateCancel,
				statusPredicateDelete);
		predicates.add(draftAndCancelStatusPredicate);

		Predicate functionalityAreaPredicate = criteriaBuilder.equal(root.get("functionalityArea"),
				FunctionalityArea.ENT_REG);
		predicates.add(functionalityAreaPredicate);

		Predicate finalPredicate = criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
		criteriaQuery.where(finalPredicate);
		List<Draft> registrationDetails = entityManager.createQuery(criteriaQuery).getResultList();
		return registrationDetails;
	}

	public List<EntRegStatus> queryForFetchingRegStatusByStatusType(List<String> categoryList) {
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<EntRegStatus> criteriaQuery = criteriaBuilder.createQuery(EntRegStatus.class);
		Root<EntRegStatus> root = criteriaQuery.from(EntRegStatus.class);
		criteriaQuery.select(root);
		Expression<String> parentExpression = root.get("statusType");
		Predicate statusTypePredicate = parentExpression.in(categoryList);
		criteriaQuery.where(statusTypePredicate);
		List<EntRegStatus> statusDetails = entityManager.createQuery(criteriaQuery).getResultList();
		return statusDetails;
	}

	public String deleteRegistrationDetailsOnEntRegDashboard(Integer draftId) {
		Draft<EntityRegistrationDTO> findDraftById = draftRepository.findDraftById(draftId);
		String status = findDraftById.getStatus();
		if (status.equals(EntityRegistrationStatus.DE_REG)) {
			findDraftById.setStatus(EntityRegistrationStatus.DELETE);
			draftRepository.deleteUserData(findDraftById);
			draftRepository.save(findDraftById);

			EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(findDraftById.getData(),
					EntityRegistrationDTO.class);
			Integer utgId = entityRegistrationDTO.getUtgId();
			UtilitiesTraderGenco findByUtgId = utilitiesTraderGencoRepository.findByUtgId(utgId);
			utilitiesTraderGencoRepository.deleteUserData(findByUtgId);
			utilitiesTraderGencoRepository.save(findByUtgId);
		}
		return status;
	}

	public EntityRegistrationDTO getRegistrationDetailsByPrimaryEmail(
			RegistrationDetailsRequestDTO registrationDetailsRequestDTO) {
		String email = registrationDetailsRequestDTO.getEmail();
		Draft<EntityRegistrationDTO> findDraftByPrimaryEmail = draftRepository.findDraftByPrimaryEmail(email);
		EntityRegistrationDTO entityRegistrationDTO = objectMapper.convertValue(findDraftByPrimaryEmail.getData(),
				EntityRegistrationDTO.class);
		String regNo = entityRegistrationDTO.getRegNo();
		String entityType = entityRegistrationDTO.getEntityType();
		EntityRegistrationDTO registrationDetails = getRegistrationDetailsByRegNo(regNo, entityType);
		return registrationDetails;
	}
}
