package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Cliente novoCliente(Cliente cliente) {
        return salvar(cliente); // Passa pelas mesmas validações e pela auditoria
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    // Método para excluir cliente
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cliente não encontrado."));
        clienteRepository.deleteById(id);

        // Registra apenas a identificação mínima (nome), sem expor os demais dados pessoais
        List<Auditoria.CampoAlterado> alteracoes = new ArrayList<>();
        AuditoriaService.adicionarSeAlterado(alteracoes, "nome", existente.getNome(), null);
        auditoriaService.registrar(AuditoriaService.EXCLUSAO_CLIENTE, "Cliente", id, alteracoes, null);
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente) {
        if (cliente == null || cliente.getNome() == null || cliente.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome do cliente é obrigatório.");
        }
        if (cliente.getTelefone() == null || cliente.getTelefone().isBlank()) {
            throw new IllegalArgumentException("O telefone do cliente é obrigatório.");
        }

        // Se o cliente não tem ID (novo cliente), salva como novo
        if (cliente.getId() == null || cliente.getId().isBlank()) {
            cliente.setId(null);
            Cliente salvo = clienteRepository.save(cliente);  // Cria um novo cliente

            // Na inclusão, registra só a identificação mínima (finalidade da auditoria)
            List<Auditoria.CampoAlterado> alteracoes = new ArrayList<>();
            AuditoriaService.adicionarSeAlterado(alteracoes, "nome", null, salvo.getNome());
            auditoriaService.registrar(AuditoriaService.INCLUSAO_CLIENTE, "Cliente", salvo.getId(), alteracoes, null);
            return salvo;
        }

        // Se já tem ID (cliente existente), atualiza
        Cliente existente = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização."));

        // Compara campo a campo para registrar valores anteriores e novos
        List<Auditoria.CampoAlterado> alteracoes = new ArrayList<>();
        AuditoriaService.adicionarSeAlterado(alteracoes, "nome", existente.getNome(), cliente.getNome());
        AuditoriaService.adicionarSeAlterado(alteracoes, "telefone", existente.getTelefone(), cliente.getTelefone());
        AuditoriaService.adicionarSeAlterado(alteracoes, "cep", existente.getCep(), cliente.getCep());
        AuditoriaService.adicionarSeAlterado(alteracoes, "logradouro", existente.getLogradouro(), cliente.getLogradouro());
        AuditoriaService.adicionarSeAlterado(alteracoes, "numero", existente.getNumero(), cliente.getNumero());
        AuditoriaService.adicionarSeAlterado(alteracoes, "complemento", existente.getComplemento(), cliente.getComplemento());
        AuditoriaService.adicionarSeAlterado(alteracoes, "bairro", existente.getBairro(), cliente.getBairro());
        AuditoriaService.adicionarSeAlterado(alteracoes, "cidade", existente.getCidade(), cliente.getCidade());
        AuditoriaService.adicionarSeAlterado(alteracoes, "estado", existente.getEstado(), cliente.getEstado());

        // Atualiza somente os campos editáveis (preserva o campo antigo "endereco")
        existente.setNome(cliente.getNome());
        existente.setTelefone(cliente.getTelefone());
        existente.setCep(cliente.getCep());
        existente.setLogradouro(cliente.getLogradouro());
        existente.setNumero(cliente.getNumero());
        existente.setComplemento(cliente.getComplemento());
        existente.setBairro(cliente.getBairro());
        existente.setCidade(cliente.getCidade());
        existente.setEstado(cliente.getEstado());

        if (alteracoes.isEmpty()) {
            return existente; // Nada mudou: não grava nem audita
        }

        Cliente atualizado = clienteRepository.save(existente);  // Atualiza o cliente existente
        auditoriaService.registrar(AuditoriaService.ALTERACAO_CLIENTE, "Cliente", atualizado.getId(), alteracoes, null);
        return atualizado;
    }

}
