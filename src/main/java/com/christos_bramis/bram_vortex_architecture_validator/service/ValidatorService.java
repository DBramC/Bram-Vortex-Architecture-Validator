package com.christos_bramis.bram_vortex_architecture_validator.service;

import com.christos_bramis.bram_vortex_architecture_validator.entity.AnalysisJob;
import com.christos_bramis.bram_vortex_architecture_validator.entity.ValidatorJob;
import com.christos_bramis.bram_vortex_architecture_validator.repository.AnalysisJobRepository;
import com.christos_bramis.bram_vortex_architecture_validator.repository.ValidatorJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ValidatorService {

    private final ValidatorJobRepository validatorJobRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public ValidatorService(ValidatorJobRepository validatorJobRepository,
                            AnalysisJobRepository analysisJobRepository,
                            ChatModel chatModel) {
        this.validatorJobRepository = validatorJobRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
    }

    public void startArchitectureValidation(String validatorJobId, String analysisJobId, String userId, String token) {
        System.out.println("\n🚀 [VORTEX-VALIDATOR] Starting Generation for Job: " + validatorJobId);

        ValidatorJob job = new ValidatorJob();
        job.setId(validatorJobId);
        job.setAnalysisJobId(analysisJobId);
        job.setUserId(userId);
        job.setStatus("GENERATING");
        validatorJobRepository.save(job);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Fetching Blueprint
                AnalysisJob analysisJob = analysisJobRepository.findById(analysisJobId)
                        .orElseThrow(() -> new RuntimeException("Analysis blueprint not found"));

                // Η ΣΩΣΤΗ ΓΡΑΜΜΗ: Χρήση του .toPrettyString() αν είναι JsonNode
                String prompt = getString(analysisJob);

                System.out.println("🧠 [VALIDATOR] Calling AI...");
                String aiResponse = chatModel.call(prompt);

                // ΔΙΑΓΝΩΣΤΙΚΟ: Βλέπουμε τι ακριβώς έστειλε το AI
                System.out.println("DEBUG AI RAW RESPONSE length: " + (aiResponse != null ? aiResponse.length() : 0));

                // 3. Robust Parsing
                Map<String, String> validatorFiles = parseResponse(aiResponse);

                if (validatorFiles == null || validatorFiles.isEmpty()) {
                    throw new RuntimeException("AI returned empty file set or invalid JSON format");
                }

                // 4. Zipping
                System.out.println("🤐 [VALIDATOR] Creating ZIP for " + validatorFiles.size() + " files...");
                byte[] zipBytes = createZipInMemory(validatorFiles);

                if (zipBytes == null || zipBytes.length < 100) {
                    throw new RuntimeException("Generated ZIP is suspiciously small (" + (zipBytes != null ? zipBytes.length : 0) + " bytes)");
                }

                // 5. Finalize
                job.setValidatorZip(zipBytes);
                job.setStatus("COMPLETED");
                validatorJobRepository.save(job);
                notifyOrchestrator(analysisJobId, "VALIDATOR", "COMPLETED", token);
                System.out.println("✅ [VALIDATOR] Success! ZIP size: " + zipBytes.length + " bytes.");

            } catch (Exception e) {
                System.err.println("❌ [VALIDATOR ERROR]: " + e.getMessage());
                job.setStatus("FAILED");
                validatorJobRepository.save(job);
                // Προσοχή: Εδώ έστελνες COMPLETED στο catch block. Το διόρθωσα σε FAILED.
                notifyOrchestrator(analysisJobId, "VALIDATOR", "FAILED", token);
            }
        });
    }

    @NotNull
    private static String getString(AnalysisJob analysisJob) {
        String blueprintJson = analysisJob.getBlueprintJson() != null ?
                analysisJob.getBlueprintJson() : "{}";

        // 2. AI Dispatch - (PLACEHOLDER PROMPT)
        String prompt = String.format("""
            You are a Principal Cloud Architect and Cross-Service Validator.
            Your objective is to validate the generated architecture and output the final corrected files.

            --- ARCHITECTURAL BLUEPRINT (JSON) ---
            %s
            --------------------------------------

            [PLACEHOLDER: Here will go the logic to check Terraform, Ansible, and Pipeline files]

            OUTPUT FORMAT (CRITICAL):
                - Respond ONLY with a SINGLE, VALID JSON object.
                - NO markdown blocks (e.g., no ```json).
                - NO conversational text.

            EXPECTED JSON SCHEMA:
            {
                "placeholder_file.txt": "File content here"
            }
            """, blueprintJson);
        return prompt;
    }

    private void notifyOrchestrator(String jobId, String service, String status, String token) {
        String url = String.format("http://repo-analyzer-svc/dashboard/internal/callback/%s?service=%s&status=%s",
                jobId, service, status);

        RestClient internalClient = RestClient.create();
        internalClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, String> parseResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            System.err.println("⚠️ [PARSING ERROR] AI response is null or empty");
            return new HashMap<>();
        }

        try {
            String clean = response.trim();
            // Αφαίρεση markdown αν το AI παρακούσει
            if (clean.startsWith("```")) {
                clean = clean.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
            }

            // Χρήση ObjectMapper για μετατροπή του String σε Map
            return objectMapper.readValue(clean, new TypeReference<HashMap<String, String>>() {});
        } catch (Exception e) {
            System.err.println("⚠️ [PARSING ERROR] Failed to convert AI response to Map: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private byte[] createZipInMemory(Map<String, String> files) throws Exception {
        // Το baos μένει έξω από το try-with-resources του zos, για να το επιστρέψουμε στο τέλος.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) continue;

                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish(); // Οριστικοποίηση του ZIP structure
            zos.flush();
        }
        return baos.toByteArray();
    }
}