package in.hertz.ctrl;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.ClientProtocolException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import in.hertz.samast.domain.ApprovalScreensRequestBO;
import in.hertz.samast.domain.DeRegisterDTO;
import in.hertz.samast.domain.DraftDTO;
import in.hertz.samast.domain.EntityRegistrationDTO;
import in.hertz.samast.domain.QCADashboardResponseDTO;
import in.hertz.samast.domain.QcaPssDraftDTO;
import in.hertz.samast.domain.RegistrationDetailsRequestDTO;
import in.hertz.samast.domain.ReportDashboardResponseDTO;
import in.hertz.samast.domain.SchedulingOnOffDTO;
import in.hertz.samast.domain.WSResp;
import in.hertz.samast.domain.exception.BusinessException;
import in.hertz.samast.entity.Draft;
import in.hertz.samast.entity.Remark;
import in.hertz.samast.util.EntityRegistrationStatus;
import in.hertz.service.EntityRegistrationService;

@RestController
@RequestMapping("/entity-registration")
public class EntityRegistrationCtrl {
	@Autowired
	private EntityRegistrationService entityRegistrationService;

	@Value("${status.check.approved.msg}")
	private String approvedMsg;

	@Value("${status.check.reject.msg}")
	private String rejectMsg;

	@Value("${status.check.underprocess.msg}")
	private String underProcessMsg;

	@Value("${status.check.hold.msg}")
	private String holdMsg;

	@Value("${status.data.not.found.msg}")
	private String dataNotFoundMsg;

	@Value("${status.data.cancel}")
	private String cancelMsg;

	@Value("${status.data.already.cancel}")
	private String alreadyCancelMsg;

	@Value("${status.data.entity.delete}")
	private String entDeleteMsg;

	@Value("${error.msg.delete}")
	private String entDeleteErrMsg;

	@PostMapping("/save-draft")
	public WSResp<Draft<EntityRegistrationDTO>> saveEntityRegistrationDraft(
			@RequestBody DraftDTO<EntityRegistrationDTO> draftDTO)
			throws ParseException, BusinessException, JsonProcessingException {
		Draft<EntityRegistrationDTO> savedDraft = entityRegistrationService.saveDraftForEntityRegistration(draftDTO);
		if (savedDraft != null) {
			return new WSResp<>(savedDraft, true, "Data Saved Successfully!");
		} else {
			return new WSResp<>(savedDraft, false, "Error In Saving The Data!");
		}
	}

	@PostMapping("/submit-draft")
	public WSResp<Draft<EntityRegistrationDTO>> submitEntityRegistrationDraft(
			@RequestBody DraftDTO<EntityRegistrationDTO> draftDTO) throws ParseException, BusinessException {
		Draft<EntityRegistrationDTO> savedDraft = entityRegistrationService.submitEntityRegistrationDraft(draftDTO);
		if (savedDraft != null) {
			return new WSResp<>(savedDraft, true, "Data Saved Successfully!");
		} else {
			return new WSResp<>(savedDraft, false, "Error In Saving The Data!");
		}
	}

	@PostMapping("/submit-draft/qca-pss")
	public WSResp<Draft<EntityRegistrationDTO>> submitEntityRegistrationDraftForQcaPss(
			@RequestBody QcaPssDraftDTO qcaPssDraftDTO) throws ParseException, BusinessException {
		Draft<EntityRegistrationDTO> savedDraft = entityRegistrationService
				.submitEntityRegistrationDraftForQcaPss(qcaPssDraftDTO);
		if (savedDraft != null) {
			return new WSResp<>(savedDraft, true, "Data Saved Successfully!");
		} else {
			return new WSResp<>(savedDraft, false, "Error In Saving The Data!");
		}
	}

	@GetMapping("draftId/{draftId}")
	public ResponseEntity<WSResp<Draft<EntityRegistrationDTO>>> getRegistrationDetailsByDraftId(
			@PathVariable Integer draftId) {
		return ResponseEntity
				.ok(new WSResp<>(entityRegistrationService.getRegistrationDetailsByDraftId(draftId), true, ""));
	}

	@GetMapping("/statusByAckNo")
	public ResponseEntity<WSResp<String>> getStatusByAckNo(@RequestParam String ackNo) {

		Draft<EntityRegistrationDTO> findDraftByAcknowledgementNo = entityRegistrationService
				.findDraftByAcknowledgementNo(ackNo);
		String status = null;
		String returnStr = null;
		if (Objects.nonNull(findDraftByAcknowledgementNo)) {
			status = findDraftByAcknowledgementNo.getStatus();
			if (StringUtils.isNotBlank(status)) {
				if (status.equalsIgnoreCase(EntityRegistrationStatus.NEW_REG)) {
					returnStr = underProcessMsg;
				} else if (status.equalsIgnoreCase(EntityRegistrationStatus.REJECT)) {
					returnStr = rejectMsg;
				} else if (status.equalsIgnoreCase(EntityRegistrationStatus.HOLD)) {
					returnStr = holdMsg;
				} else if (status.equalsIgnoreCase(EntityRegistrationStatus.REG)) {
					returnStr = approvedMsg;
				} else if (status.equalsIgnoreCase(EntityRegistrationStatus.CANCELLED)) {
					returnStr = alreadyCancelMsg;
				}
				return ResponseEntity.ok(new WSResp<>("", true, returnStr));
			}
		} else {
			returnStr = dataNotFoundMsg;
			return ResponseEntity.ok(new WSResp<>(null, false, returnStr));
		}
		return null;
	}

	@PostMapping("/registrationCancel")
	public ResponseEntity<WSResp<String>> getRegistrationCancel(@RequestParam String ackNo) {
		String returnStr = null;
		Draft<EntityRegistrationDTO> draft = entityRegistrationService.cancelRegistrationByAckNo(ackNo);
		if (draft == null) {
			returnStr = dataNotFoundMsg;
			return ResponseEntity.ok(new WSResp<>("", false, returnStr));
		} else {
			returnStr = cancelMsg;
			return ResponseEntity.ok(new WSResp<>(null, true, returnStr));
		}

	}

	@PostMapping("/sldc-approval")
	public WSResp<Remark> departmentApprovals(@RequestBody ApprovalScreensRequestBO approvalScreensRequestBO)
			throws JsonProcessingException, ClientProtocolException, IOException, ParseException {
		Remark remarkObj = entityRegistrationService.departmentApprovals(approvalScreensRequestBO);
		if (remarkObj != null) {
			return new WSResp<>(remarkObj, true, "Data Saved Successfully!");
		} else {
			return new WSResp<>(remarkObj, false, "Error In Saving The Data!");
		}
	}

	@PostMapping("/regNo")
	public WSResp<EntityRegistrationDTO> getRegistrationDetailsByDraftId(@RequestParam String regNo,
			@RequestParam String entityType) {
		EntityRegistrationDTO registrationDetailsByRegNo = entityRegistrationService
				.getRegistrationDetailsByRegNo(regNo, entityType);
		if (registrationDetailsByRegNo != null) {
			return new WSResp<>(registrationDetailsByRegNo, true, "Data Fetched Successfully!");
		} else {
			return new WSResp<>(registrationDetailsByRegNo, false, "Error In Fetching The Data!");
		}
	}

	@PostMapping("/scheduling")
	public ResponseEntity<WSResp<String>> setSchedulingOnOrOff(@RequestBody SchedulingOnOffDTO schedulingOnOffDTO) {
		String returnStr = null;
		String setSchedulingOnOrOff = entityRegistrationService.setSchedulingOnOrOff(schedulingOnOffDTO);
		if (setSchedulingOnOrOff.equals(EntityRegistrationStatus.ENB)) {
			returnStr = "Scheduling Is Enabled Successfully.";
		} else {
			returnStr = "Scheduling Is Disabled Successfully.";
		}
		return ResponseEntity.ok(new WSResp<>(returnStr, true, ""));
	}

	@PostMapping("/sldc/de-register")
	public ResponseEntity<WSResp<String>> deRegisterBySLDC(@RequestBody DeRegisterDTO deRegisterDTO) {
		String setDeRegister = entityRegistrationService.deRegisterBySLDC(deRegisterDTO);
		return ResponseEntity.ok(new WSResp<>(setDeRegister, true, ""));
	}

	@PostMapping("/entity/de-register")
	public ResponseEntity<WSResp<String>> deRegisterByEntity(@RequestParam String regNo) {
		String setDeRegister = entityRegistrationService.deRegisterByEntity(regNo);
		return ResponseEntity.ok(new WSResp<>(null, true, setDeRegister));
	}

	@PostMapping("/dashboard")
	public ResponseEntity<WSResp<List<Draft>>> getRegistrationDetailsForDashboard(
			@RequestBody Map<String, Object> requestBody) {
		return ResponseEntity
				.ok(new WSResp<>(entityRegistrationService.getRegistrationDetailsForDashboard(requestBody), true, ""));
	}

	@GetMapping("/dashboard/qca/{qcaUtgId}")
	public ResponseEntity<WSResp<QCADashboardResponseDTO>> getRegistrationDetailsForQcaDashboard(
			@PathVariable Integer qcaUtgId) {
		return ResponseEntity
				.ok(new WSResp<>(entityRegistrationService.getRegistrationDetailsForQcaDashboard(qcaUtgId), true, ""));
	}

	@PostMapping("/dashboard/report")
	public ResponseEntity<WSResp<ReportDashboardResponseDTO>> getRegistrationDetailsForReportingDashboard(
			@RequestBody Map<String, Object> requestBody) throws ParseException {
		return ResponseEntity.ok(new WSResp<>(
				entityRegistrationService.getRegistrationDetailsForReportingDashboard(requestBody), true, ""));
	}

	@DeleteMapping("dashboard/draftId/{draftId}")
	public WSResp<String> deleteRegistrationDetailsOnEntRegDashboard(@PathVariable Integer draftId) {
		String status = entityRegistrationService.deleteRegistrationDetailsOnEntRegDashboard(draftId);
		if (status.equals(EntityRegistrationStatus.DE_REG)) {
			return new WSResp<>(null, true, entDeleteMsg);
		} else {
			return new WSResp<>(null, false, entDeleteErrMsg);
		}
	}

	@PostMapping("/details")
	public WSResp<EntityRegistrationDTO> getRegistrationDetailsByPrimaryEmail(
			@RequestBody RegistrationDetailsRequestDTO registrationDetailsRequestDTO) {
		EntityRegistrationDTO registrationDetailsByRegNo = entityRegistrationService
				.getRegistrationDetailsByPrimaryEmail(registrationDetailsRequestDTO);
		if (registrationDetailsByRegNo != null) {
			return new WSResp<>(registrationDetailsByRegNo, true, "Data Fetched Successfully!");
		} else {
			return new WSResp<>(registrationDetailsByRegNo, false, "Error In Fetching The Data!");
		}
	}
}
